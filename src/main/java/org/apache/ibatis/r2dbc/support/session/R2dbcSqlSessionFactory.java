package org.apache.ibatis.r2dbc.support.session;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.TransactionDefinition;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.TransactionIsolationLevel;

public interface R2dbcSqlSessionFactory {

  R2dbcSqlSession openSession();

  R2dbcSqlSession openSession(boolean autoCommit);

  R2dbcSqlSession openSession(Connection connection);

  R2dbcSqlSession openSession(TransactionIsolationLevel level);

  R2dbcSqlSession openSession(ExecutorType execType);

  R2dbcSqlSession openSession(ExecutorType execType, boolean autoCommit);

  R2dbcSqlSession openSession(ExecutorType execType, TransactionDefinition level);

  R2dbcSqlSession openSession(ExecutorType execType, Connection connection);

  R2dbcConfiguration getConfiguration();

}
