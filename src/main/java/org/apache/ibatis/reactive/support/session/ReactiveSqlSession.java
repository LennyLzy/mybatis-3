package org.apache.ibatis.reactive.support.session;

import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.reactive.support.ReactiveConfiguration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReactiveSqlSession {

  <T> Mono<T> selectOne(String statement);

  <T> Mono<T> selectOne(String statement, Object parameter);

  <T> Flux<T> selectList(String statement);

  <T> Flux<T> selectList(String statement, Object parameter);

  Mono<Integer> insert(String statement);

  Mono<Integer> insert(String statement, Object parameter);

  Mono<Integer> update(String statement);

  Mono<Integer> update(String statement, Object parameter);

  Mono<Integer> delete(String statement);

  Mono<Integer> delete(String statement, Object parameter);

  Mono<Void> commit();

  Mono<Void> commit(boolean force);

  Mono<Void> rollback();

  Mono<Void> rollback(boolean force);

//  Flux<BatchResult> flushStatements();

  Mono<Void> close();

  Mono<Void> clearCache();

  ReactiveConfiguration getConfiguration();

  <T> T getMapper(Class<T> type);

}
