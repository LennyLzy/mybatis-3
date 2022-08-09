package org.apache.ibatis.r2dbc.support.executor;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.r2dbc.support.executor.result.R2dbcResultHandler;
import org.apache.ibatis.r2dbc.support.session.R2dbcConfiguration;
import org.apache.ibatis.r2dbc.support.transaction.R2dbcTransaction;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.RowBounds;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.SQLException;

public class R2dbcSimpleExecutor implements R2dbcExecutor {
  public R2dbcSimpleExecutor(R2dbcTransaction tx, R2dbcConfiguration r2dbcConfiguration) {
  }

  @Override
  public Mono<Integer> update(MappedStatement ms, Object parameter) throws SQLException {
    return null;
  }

  @Override
  public <E> Flux<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, R2dbcResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) throws SQLException {
    return null;
  }

  @Override
  public <E> Flux<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, R2dbcResultHandler resultHandler) throws SQLException {
    return null;
  }

  @Override
  public <E> Mono<Cursor<E>> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    return null;
  }

  @Override
  public Flux<BatchResult> flushStatements() throws SQLException {
    return null;
  }

  @Override
  public Mono<Void> commit(boolean required) throws SQLException {
    return null;
  }

  @Override
  public Mono<Void> rollback(boolean required) throws SQLException {
    return null;
  }

  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    return null;
  }

  @Override
  public Mono<Boolean> isCached(MappedStatement ms, CacheKey key) {
    return null;
  }

  @Override
  public Mono<Void> clearLocalCache() {
    return null;
  }

  @Override
  public Mono<Void> deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    return null;
  }

  @Override
  public Mono<Void> close(boolean forceRollback) {
    return null;
  }

  @Override
  public Mono<Boolean> isClosed() {
    return null;
  }
}
