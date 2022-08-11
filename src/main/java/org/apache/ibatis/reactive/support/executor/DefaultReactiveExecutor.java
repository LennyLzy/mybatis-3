/*
 *    Copyright 2009-2022 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reactive.support.executor;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.IsolationLevel;
import io.r2dbc.spi.Statement;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reactive.support.ProxyInstanceFactory;
import org.apache.ibatis.reactive.support.ReactiveConfiguration;
import org.apache.ibatis.reactive.support.executor.key.*;
import org.apache.ibatis.reactive.support.executor.parameter.PreparedReactiveStatement;
import org.apache.ibatis.reactive.support.executor.result.RowResultWrapper;
import org.apache.ibatis.reactive.support.executor.resultset.R2dbcResultSetsHandler;
import org.apache.ibatis.reactive.support.executor.support.ReactiveExecutorContext;
import org.apache.ibatis.reactive.support.session.MybatisReactiveContextManager;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.RowBounds;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class DefaultReactiveExecutor extends BaseReactiveExecutor {

  private static final Log log = LogFactory.getLog(DefaultReactiveExecutor.class);

  private IsolationLevel isolationLevel;

  private Boolean autoCommit;

  public DefaultReactiveExecutor(ReactiveConfiguration configuration, IsolationLevel isolationLevel, Boolean autoCommit) {
    super(configuration);
    this.isolationLevel = isolationLevel;
    this.autoCommit = autoCommit;
  }

  @Override
  protected Mono<Integer> doUpdateWithConnection(Connection connection, MappedStatement mappedStatement, Object parameter) {
    return MybatisReactiveContextManager.currentContext()
      .doOnNext(reactiveExecutorContext -> {
        if (log.isTraceEnabled()) {
          log.trace("Do update with connection from context : " + reactiveExecutorContext);
        }
      })
      .map(ReactiveExecutorContext::getR2dbcStatementLog)
      .flatMap(r2dbcStatementLog -> {
        R2dbcKeyGenerator r2dbcKeyGenerator = this.getR2dbcKeyGenerator(mappedStatement);
        return r2dbcKeyGenerator.processSelectKey(KeyGeneratorType.SELECT_KEY_BEFORE, mappedStatement, parameter)
          .flatMap(ignoreResult -> {
            BoundSql boundSql = mappedStatement.getBoundSql(parameter);
            boolean isReturnedGeneratedKeys = KeyGeneratorType.SIMPLE_RETURN.equals(r2dbcKeyGenerator.keyGeneratorType());
            Statement statement = connection.createStatement(boundSql.getSql());
            PreparedStatement preparedStatement = prepare(statement);
            DefaultParameterHandler parameterHandler = new DefaultParameterHandler(mappedStatement, parameter, boundSql);
            parameterHandler.setParameters(preparedStatement);
            statement = intercept(statement);
            Statement finalStatement = statement;
            return Mono.just(isReturnedGeneratedKeys)
              .filter(condition -> condition)
              .flatMapMany(condition -> finalStatement.execute())
                .checkpoint("SQL: \"" + boundSql + "\" [DefaultReactiveExecutor]")
//                .take(mappedStatement.getKeyProperties().length, true)
                .flatMap(result -> result.map((row, rowMetadata) -> {
                  RowResultWrapper rowResultWrapper = new RowResultWrapper(row, rowMetadata, configuration);
                  return r2dbcKeyGenerator.processGeneratedKeyResult(rowResultWrapper, parameter);
                }))
              .switchIfEmpty(Flux
                .from(statement.execute())
                .checkpoint("SQL: \"" + boundSql + "\" [DefaultReactiveExecutor]")
                .flatMap(result -> Mono.from(result.getRowsUpdated()))
              )
              .collect(Collectors.summingInt(Integer::intValue))
              .defaultIfEmpty(0)
              .doOnNext(r2dbcStatementLog::logUpdates)
              .flatMap(totalUpdateRowCount -> r2dbcKeyGenerator.processSelectKey(KeyGeneratorType.SELECT_KEY_AFTER, mappedStatement, parameter)
                .flatMap(ignore -> Mono.just(totalUpdateRowCount))
              );
          });
      });
  }

  @Override
  protected <T> Flux<T> doQueryWithConnection(Connection connection, MappedStatement mappedStatement, Object parameter) {
    return MybatisReactiveContextManager.currentContext()
      .doOnNext(reactiveExecutorContext -> {
        if (log.isTraceEnabled()) {
          log.trace("Do query with connection from context : " + reactiveExecutorContext);
        }
      })
      .map(ReactiveExecutorContext::getR2dbcStatementLog)
      .flatMapMany(statementLogger -> {
        BoundSql boundSql = mappedStatement.getBoundSql(parameter);
        Statement statement = connection.createStatement(boundSql.getSql());
        PreparedStatement preparedStatement = prepare(statement);
        DefaultParameterHandler parameterHandler = new DefaultParameterHandler(mappedStatement, parameter, boundSql);
        parameterHandler.setParameters(preparedStatement);
        statement = intercept(statement);
        R2dbcResultSetsHandler resultSetsHandler = new R2dbcResultSetsHandler(getConfiguration(), mappedStatement, null);
        return Flux.from(statement.execute())
          .checkpoint("SQL: \"" + boundSql + "\" [DefaultReactiveExecutor]")
          .concatMap(result -> Flux.from(result.map((row, rowMetadata) -> {
              RowResultWrapper rowResultWrapper = new RowResultWrapper(row, rowMetadata, configuration);
              try {
                resultSetsHandler.handleRowResult(rowResultWrapper);
              } catch (SQLException e) {
                e.printStackTrace();
              }
              return rowResultWrapper;
            })
            ).collectList().map(rws -> {
              for (RowResultWrapper rw : rws) {
                try {
                  resultSetsHandler.handleNestedRowResult(rw);
                } catch (SQLException e) {
                  e.printStackTrace();
                }
              }
              List<Object> list = new ArrayList(resultSetsHandler.getMultipleResults());
              return list;
            })
          ).doOnNext(list -> resultSetsHandler.nextResult())
          .collectList()
          .flatMapMany(results ->
            Flux.fromStream(((List<T>) resultSetsHandler.collapseSingleResultList(results)).stream())
          );
      });
  }

  private PreparedStatement prepare(Statement statement) {
    return ProxyInstanceFactory.newInstanceOfInterfaces(PreparedStatement.class,
      () -> new PreparedReactiveStatement(statement)
    );
  }

  private Statement intercept(Statement statement) {
    ReactiveConfiguration configuration = getConfiguration();
    return (Statement) configuration.getInterceptorChain().pluginAll(statement);
  }

  private R2dbcKeyGenerator getR2dbcKeyGenerator(MappedStatement mappedStatement) {
    String[] keyColumns = mappedStatement.getKeyColumns();
    boolean hasKeyColumns = keyColumns != null && keyColumns.length != 0;
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    boolean useJdbc3KeyGenerator = keyGenerator instanceof Jdbc3KeyGenerator && hasKeyColumns;
    if (useJdbc3KeyGenerator) {
      return new DefaultR2dbcKeyGenerator(mappedStatement, super.configuration);
    }
    if (keyGenerator instanceof SelectKeyGenerator) {
      return new SelectR2dbcKeyGenerator((SelectKeyGenerator) keyGenerator, super.configuration, this);
    }
    return NoKeyR2dbcKeyGenerator.getInstance();
  }

}