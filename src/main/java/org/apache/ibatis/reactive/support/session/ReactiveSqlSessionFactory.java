package org.apache.ibatis.reactive.support.session;

import io.r2dbc.spi.IsolationLevel;
import org.apache.ibatis.reactive.support.ReactiveConfiguration;

public interface ReactiveSqlSessionFactory {

  ReactiveSqlSession openSession();

  ReactiveSqlSession openSession(Boolean autoCommit);

  ReactiveSqlSession openSession(IsolationLevel isolationLevel, Boolean autoCommit);

  ReactiveConfiguration getConfiguration();

}
