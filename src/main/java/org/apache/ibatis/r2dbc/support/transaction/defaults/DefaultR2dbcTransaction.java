package org.apache.ibatis.r2dbc.support.transaction.defaults;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.TransactionDefinition;
import org.apache.ibatis.r2dbc.support.transaction.R2dbcTransaction;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

public class DefaultR2dbcTransaction implements R2dbcTransaction {

  private ConnectionFactory connectionFactory;
  private TransactionDefinition txDefinition;
  private boolean autoCommit;

  public DefaultR2dbcTransaction(ConnectionFactory connectionFactory, TransactionDefinition txDefinition, boolean autoCommit) {
    this.connectionFactory = connectionFactory;
    this.txDefinition = txDefinition;
    this.autoCommit = autoCommit;
  }

  @Override
  public Publisher<Connection> getConnection() {
    return null;
  }

  @Override
  public Mono<Void> commit() {
    return null;
  }

  @Override
  public Mono<Void> rollback() {
    return null;
  }

  @Override
  public Mono<Void> close() {
    return null;
  }

  @Override
  public Mono<Integer> getTimeout() {
    return null;
  }
}
