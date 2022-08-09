package org.apache.ibatis.r2dbc.support.transaction;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.TransactionDefinition;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.reactivestreams.Publisher;

import java.util.Properties;

public interface R2dbcTransactionFactory {

  default void setProperties(Properties props) {
    // NOP
  }

  R2dbcTransaction newTransaction(Connection conn);

  R2dbcTransaction newTransaction(ConnectionFactory connectionFactory, TransactionDefinition level, boolean autoCommit);

}
