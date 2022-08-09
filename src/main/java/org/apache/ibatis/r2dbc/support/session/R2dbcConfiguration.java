package org.apache.ibatis.r2dbc.support.session;

import io.r2dbc.proxy.util.Assert;
import io.r2dbc.spi.ConnectionFactory;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.InterceptorChain;
import org.apache.ibatis.r2dbc.support.executor.*;
import org.apache.ibatis.r2dbc.support.transaction.R2dbcTransaction;
import org.apache.ibatis.r2dbc.support.transaction.R2dbcTransactionFactory;
import org.apache.ibatis.r2dbc.support.transaction.defaults.DefaultR2dbcTransactionFactory;
import org.apache.ibatis.reactive.support.executor.support.R2dbcStatementLog;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;

public class R2dbcConfiguration {

  private final R2dbcStatementLogFactory r2dbcStatementLogFactory = new R2dbcStatementLogFactory(this);
  private final ConnectionFactory connectionFactory;
  private final R2dbcTransactionFactory transactionFactory;

  private Configuration delegateConfiguration;
  private volatile InterceptorChain interceptorChain;

  public R2dbcConfiguration(ConnectionFactory connectionFactory) {
    this.connectionFactory = connectionFactory;
    this.transactionFactory = new DefaultR2dbcTransactionFactory();
  }

  public ExecutorType getDefaultExecutorType() {
    Assert.requireNonNull(this.delegateConfiguration, "origin configuration is null.");
    return this.delegateConfiguration.getDefaultExecutorType();
  }

  public String getLogPrefix() {
    Assert.requireNonNull(this.delegateConfiguration, "origin configuration is null.");
    return this.delegateConfiguration.getLogPrefix();
  }

  public R2dbcExecutor newExecutor(R2dbcTransaction tx, ExecutorType executorType) {
    executorType = executorType == null ? delegateConfiguration.getDefaultExecutorType() : executorType;
    executorType = executorType == null ? ExecutorType.SIMPLE : executorType;
    R2dbcExecutor executor;
    if (ExecutorType.BATCH == executorType) {
      executor = new R2dbcBatchExecutor(tx,this);
    } else if (ExecutorType.REUSE == executorType) {
      executor = new R2dbcReuseExecutor(tx,this);
    } else {
      executor = new R2dbcSimpleExecutor(tx,this);
    }
    if (delegateConfiguration.isCacheEnabled()) {
      executor = new R2dbcCachingExecutor(executor);
    }
    executor = (R2dbcExecutor) interceptorChain.pluginAll(executor);
    return executor;
  }

  public MappedStatement getMappedStatement(String statement) {
    return delegateConfiguration.getMappedStatement(statement);
  }

  public R2dbcTransactionFactory getTransactionFactory() {
    return this.transactionFactory;
  }

  public R2dbcStatementLog getR2dbcStatementLog(MappedStatement mappedStatement) {
    return this.r2dbcStatementLogFactory.getR2dbcStatementLog(mappedStatement);
  }

}
