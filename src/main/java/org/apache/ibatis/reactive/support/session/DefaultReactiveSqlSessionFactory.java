/*
 *    Copyright 2009-2022 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reactive.support.session;

import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.IsolationLevel;
import org.apache.ibatis.reactive.support.ReactiveConfiguration;

public class DefaultReactiveSqlSessionFactory implements ReactiveSqlSessionFactory {

  private ConnectionFactory connectionFactory;

  private ReactiveConfiguration configuration;

  public DefaultReactiveSqlSessionFactory(ConnectionFactory connectionFactory, ReactiveConfiguration configuration) {
    this.connectionFactory = connectionFactory;
    this.configuration = configuration;
  }

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
