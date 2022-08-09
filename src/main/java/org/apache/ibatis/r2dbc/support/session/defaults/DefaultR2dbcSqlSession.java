package org.apache.ibatis.r2dbc.support.session.defaults;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.IsolationLevel;
import io.r2dbc.spi.TransactionDefinition;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.r2dbc.support.executor.R2dbcExecutor;
import org.apache.ibatis.r2dbc.support.executor.result.R2dbcResultHandler;
import org.apache.ibatis.r2dbc.support.session.R2dbcConfiguration;
import org.apache.ibatis.r2dbc.support.session.R2dbcSqlSession;
import org.apache.ibatis.reactive.support.executor.support.R2dbcStatementLog;
import org.apache.ibatis.reactive.support.executor.support.ReactiveExecutorContext;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.TransactionIsolationLevel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DefaultR2dbcSqlSession implements R2dbcSqlSession {

  private R2dbcConfiguration configuration;
  R2dbcExecutor executor;

  TransactionDefinition level;
  boolean autoCommit;

  public DefaultR2dbcSqlSession(R2dbcConfiguration configuration, R2dbcExecutor executor, TransactionDefinition level, boolean autoCommit) {
    this.configuration = configuration;
    this.executor = executor;
    this.level = level;
    this.autoCommit = autoCommit;
  }

  @Override
  public <T> Mono<T> selectOne(String statement) throws SQLException {
    return this.selectOne(statement, null);
  }

  @Override
  public <T> Mono<T> selectOne(String statement, Object parameter) throws SQLException {
    return this.<T>selectList(statement, parameter)
      .buffer(2)
      .flatMap(results -> {
        if (results.isEmpty()) {
          return Mono.empty();
        }
        if (results.size() > 1) {
          return Mono.error(new TooManyResultsException("Expected one result (or null) to be returned by selectOne()"));
        }
        return Mono.justOrEmpty(results.get(0));
      }).singleOrEmpty();
  }

  @Override
  public <E> Flux<E> selectList(String statement) throws SQLException {
    return this.selectList(statement, null, RowBounds.DEFAULT);
  }

  @Override
  public <E> Flux<E> selectList(String statement, Object parameter) throws SQLException {
    return this.selectList(statement, parameter, RowBounds.DEFAULT);
  }

  @Override
  public <E> Flux<E> selectList(String statement, Object parameter, RowBounds rowBounds) throws SQLException {
    return this.selectList(statement, parameter, rowBounds, null);
  }

  public  <E> Flux<E> selectList(String statement, Object parameter, RowBounds rowBounds, R2dbcResultHandler handler) throws SQLException {
    MappedStatement mappedStatement = configuration.getMappedStatement(statement);
    Object wrappedParameter = ParamNameResolver.wrapToMapIfCollection(parameter, null);
    return executor.query(mappedStatement, wrappedParameter, rowBounds, handler)
      .contextWrite(context -> initLogContext(context, this.configuration.getR2dbcStatementLog(mappedStatement)));
  }

  @Override
  public <K, V> Mono<Map<K, V>> selectMap(String statement, String mapKey) {
    return null;
  }

  @Override
  public <K, V> Mono<Map<K, V>> selectMap(String statement, Object parameter, String mapKey) {
    return null;
  }

  @Override
  public <K, V> Mono<Map<K, V>> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds) {
    return null;
  }

  @Override
  public Mono<Void> select(String statement, Object parameter, R2dbcResultHandler handler) {
    return null;
  }

  @Override
  public Mono<Void> select(String statement, R2dbcResultHandler handler) {
    return null;
  }

  @Override
  public Mono<Void> select(String statement, Object parameter, RowBounds rowBounds, R2dbcResultHandler handler) {
    return null;
  }

  @Override
  public Mono<Integer> insert(String statement) {
    return null;
  }

  @Override
  public Mono<Integer> insert(String statement, Object parameter) {
    return null;
  }

  @Override
  public Mono<Integer> update(String statement) {
    return null;
  }

  @Override
  public Mono<Integer> update(String statement, Object parameter) {
    return null;
  }

  @Override
  public Mono<Integer> delete(String statement) {
    return null;
  }

  @Override
  public Mono<Integer> delete(String statement, Object parameter) {
    return null;
  }

  @Override
  public Mono<Void> commit() {
    return null;
  }

  @Override
  public Mono<Void> commit(boolean force) {
    return null;
  }

  @Override
  public Mono<Void> rollback() {
    return null;
  }

  @Override
  public Mono<Void> rollback(boolean force) {
    return null;
  }

  @Override
  public Flux<BatchResult> flushStatements() {
    return null;
  }

  @Override
  public Mono<Void> close() {
    return null;
  }

  @Override
  public Mono<Void> clearCache() {
    return null;
  }

  @Override
  public R2dbcConfiguration getConfiguration() {
    return null;
  }

  @Override
  public <T> T getMapper(Class<T> type) {
    return null;
  }

  @Override
  public Mono<Connection> getConnection() {
    return null;
  }

  public Context initLogContext(Context context, R2dbcStatementLog r2dbcStatementLog) {
//    Optional<ReactiveExecutorContext> optionalContext = context.getOrEmpty(ReactiveExecutorContext.class)
//      .map(ReactiveExecutorContext.class::cast);
//    if (optionalContext.isPresent()) {
//      ReactiveExecutorContext reactiveExecutorContext = optionalContext.get();
//      if (this.withTransaction) {
//        reactiveExecutorContext.setWithTransaction();
//      }
//      reactiveExecutorContext.setR2dbcStatementLog(r2dbcStatementLog);
//      return context;
//    }
//    ReactiveExecutorContext newContext = new ReactiveExecutorContext(autoCommit, (IsolationLevel)level);
//    newContext.setR2dbcStatementLog(r2dbcStatementLog);
//    if (this.withTransaction) {
//      newContext.setWithTransaction();
//    }
//    return context.put(ReactiveExecutorContext.class, newContext);
  }

}
