package org.apache.ibatis.r2dbc.support.transaction.defaults;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.TransactionDefinition;
import org.apache.ibatis.r2dbc.support.transaction.R2dbcTransaction;
import org.apache.ibatis.r2dbc.support.transaction.R2dbcTransactionFactory;

public class DefaultR2dbcTransactionFactory implements R2dbcTransactionFactory {

  @Override
  public R2dbcTransaction newTransaction(Connection conn) {
    return null;
  }

  @Override
  public R2dbcTransaction newTransaction(ConnectionFactory connectionFactory, TransactionDefinition level, boolean autoCommit) {
      return new DefaultR2dbcTransaction(connectionFactory, level, autoCommit);
  }

}
