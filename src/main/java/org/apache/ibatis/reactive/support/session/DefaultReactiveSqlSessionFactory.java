package org.apache.ibatis.reactive.support.session;

import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.IsolationLevel;
import org.apache.ibatis.reactive.support.ReactiveConfiguration;

public class DefaultReactiveSqlSessionFactory implements ReactiveSqlSessionFactory {

  private ConnectionFactory connectionFactory;

  private ReactiveConfiguration configuration;

  @Override
  public ReactiveSqlSession openSession() {
    return openSession(false);
  }

  @Override
  public ReactiveSqlSession openSession(Boolean autoCommit) {
    return openSession(IsolationLevel.READ_COMMITTED, autoCommit);
  }

  @Override
  public ReactiveSqlSession openSession(IsolationLevel isolationLevel, Boolean autoCommit) {
    return new DefaultReactiveSqlSession(configuration, isolationLevel, autoCommit);
  }

  @Override
  public ReactiveConfiguration getConfiguration() {
    return this.configuration;
  }

}
