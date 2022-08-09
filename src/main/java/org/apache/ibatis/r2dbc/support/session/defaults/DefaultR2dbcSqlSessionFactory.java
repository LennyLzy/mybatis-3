package org.apache.ibatis.r2dbc.support.session.defaults;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.TransactionDefinition;
import org.apache.ibatis.r2dbc.support.executor.R2dbcExecutor;
import org.apache.ibatis.r2dbc.support.session.R2dbcConfiguration;
import org.apache.ibatis.r2dbc.support.session.R2dbcSqlSession;
import org.apache.ibatis.r2dbc.support.session.R2dbcSqlSessionFactory;
import org.apache.ibatis.r2dbc.support.transaction.R2dbcTransaction;
import org.apache.ibatis.r2dbc.support.transaction.R2dbcTransactionFactory;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.TransactionIsolationLevel;

public class DefaultR2dbcSqlSessionFactory implements R2dbcSqlSessionFactory {

  private R2dbcConfiguration configuration;

  private ConnectionFactory connectionFactory;

  public DefaultR2dbcSqlSessionFactory(R2dbcConfiguration r2dbcConfiguration, ConnectionFactory connectionFactory) {
    this.configuration = r2dbcConfiguration;
    this.connectionFactory = connectionFactory;
  }

  @Override
  public R2dbcSqlSession openSession() {
    return openSession(true);
  }

  @Override
  public R2dbcSqlSession openSession(boolean autoCommit) {
    return doOpenR2dbcSqlSession(configuration.getDefaultExecutorType(), null, autoCommit);
  }

  @Override
  public R2dbcSqlSession openSession(Connection connection) {
    return null;
  }

  @Override
  public R2dbcSqlSession openSession(TransactionIsolationLevel level) {
    return doOpenR2dbcSqlSession(configuration.getDefaultExecutorType(), null, false);
  }

  @Override
  public R2dbcSqlSession openSession(ExecutorType execType) {
    return doOpenR2dbcSqlSession(execType, null, false);
  }

  @Override
  public R2dbcSqlSession openSession(ExecutorType execType, boolean autoCommit) {
    return doOpenR2dbcSqlSession(execType, null, autoCommit);
  }

  @Override
  public R2dbcSqlSession openSession(ExecutorType execType, TransactionDefinition level) {
    return doOpenR2dbcSqlSession(execType, level, false);
  }

  @Override
  public R2dbcSqlSession openSession(ExecutorType execType, Connection connection) {
    return null;
  }

  @Override
  public R2dbcConfiguration getConfiguration() {
    return this.configuration;
  }

  public ConnectionFactory getConnectionFactory() {
    return this.connectionFactory;
  }

  private R2dbcSqlSession doOpenR2dbcSqlSession(ExecutorType execType, TransactionDefinition level, boolean autoCommit) {
    final R2dbcTransactionFactory transactionFactory = configuration.getTransactionFactory();
    final R2dbcTransaction tx = transactionFactory.newTransaction(this.connectionFactory, level, autoCommit);
    final R2dbcExecutor executor = configuration.newExecutor(tx,execType);
    return new DefaultR2dbcSqlSession(configuration, executor, level, autoCommit);
  }

}
