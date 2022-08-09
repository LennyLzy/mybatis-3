package org.apache.ibatis.r2dbc.support.transaction;

import io.r2dbc.spi.Connection;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

public interface R2dbcTransaction {

  Publisher<Connection> getConnection();

  Mono<Void> commit();

  Mono<Void> rollback();

  Mono<Void> close();

  Mono<Integer> getTimeout();

}
