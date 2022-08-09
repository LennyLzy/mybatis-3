package org.apache.ibatis.r2dbc.support.executor;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.r2dbc.support.executor.result.R2dbcResultHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.RowBounds;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.SQLException;


public interface R2dbcExecutor {

  Mono<Integer> update(MappedStatement ms, Object parameter) throws SQLException;

  <E> Flux<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, R2dbcResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) throws SQLException;

  <E> Flux<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, R2dbcResultHandler resultHandler) throws SQLException;

  <E> Mono<Cursor<E>> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException;

  Flux<BatchResult> flushStatements() throws SQLException;

  Mono<Void> commit(boolean required) throws SQLException;

  Mono<Void> rollback(boolean required) throws SQLException;

  CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql);

  Mono<Boolean> isCached(MappedStatement ms, CacheKey key);

  Mono<Void> clearLocalCache();

  Mono<Void> deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType);

//  Transaction getTransaction();

  Mono<Void> close(boolean forceRollback);

  Mono<Boolean> isClosed();

//  void setExecutorWrapper(Executor executor);

}
