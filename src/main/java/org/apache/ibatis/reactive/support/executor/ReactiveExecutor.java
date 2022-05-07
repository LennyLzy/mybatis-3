package org.apache.ibatis.reactive.support.executor;

import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.RowBounds;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReactiveExecutor {

  Mono<Integer> update(MappedStatement mappedStatement, Object parameter);

  <T> Flux<T> query(MappedStatement mappedStatement, Object parameter);

  Mono<Void> commit(boolean required);

  Mono<Void> rollback(boolean required);

  Mono<Void> close(boolean forceRollback);

}
