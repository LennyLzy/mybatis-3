package org.apache.ibatis.reactive.support.session;

import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.IsolationLevel;
import io.r2dbc.spi.Option;
import io.r2dbc.spi.TransactionDefinition;
import org.apache.ibatis.reactive.support.ReactiveConfiguration;
import reactor.core.publisher.Mono;

public class DefaultReactiveSqlSessionFactory implements ReactiveSqlSessionFactory {

  private ConnectionFactory connectionFactory;

  private ReactiveConfiguration configuration;

  @Override
  public ReactiveSqlSession openSession() {
    return null;
  }

  @Override
  public ReactiveConfiguration getConfiguration() {
    return this.configuration;
  }

}
