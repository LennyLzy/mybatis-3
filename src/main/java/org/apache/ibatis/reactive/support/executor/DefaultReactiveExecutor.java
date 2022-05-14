package org.apache.ibatis.reactive.support.executor;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.IsolationLevel;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reactive.support.ProxyInstanceFactory;
import org.apache.ibatis.reactive.support.ReactiveConfiguration;
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
import java.util.List;
import java.util.Objects;

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
    return null;
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
        R2dbcResultSetsHandler resultSetsHandler = new R2dbcResultSetsHandler(getConfiguration(), mappedStatement);
        return Flux.from(statement.execute())
          .checkpoint("SQL: \"" + boundSql + "\" [DefaultReactiveExecutor]")
          .concatMap(result -> resultSetsHandler.handleResultSet(result));
//          .doOnComplete(() -> statementLogger.logTotal(resultSetsHandler.getResultRowTotalCount()));
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

}
