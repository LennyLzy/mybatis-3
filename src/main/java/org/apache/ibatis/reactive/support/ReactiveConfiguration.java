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
package org.apache.ibatis.reactive.support;

import io.r2dbc.spi.ConnectionFactory;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.InterceptorChain;
import org.apache.ibatis.reactive.support.binding.SqlSessionProxy;
import org.apache.ibatis.reactive.support.executor.support.R2dbcStatementLog;
import org.apache.ibatis.reactive.support.executor.support.R2dbcStatementLogFactory;
import org.apache.ibatis.reactive.support.session.ReactiveSqlSession;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;


public class ReactiveConfiguration extends Configuration {

  private Configuration configuration;
  private final R2dbcStatementLogFactory r2dbcStatementLogFactory = new R2dbcStatementLogFactory(this);
  private final ConnectionFactory connectionFactory;

  public ReactiveConfiguration(ConnectionFactory connectionFactory) {
    this.connectionFactory = connectionFactory;
  }

  public ConnectionFactory getConnectionFactory() {
    return connectionFactory;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

  public R2dbcStatementLog getR2dbcStatementLog(MappedStatement mappedStatement) {
    return this.r2dbcStatementLogFactory.getR2dbcStatementLog(mappedStatement);
  }

  public <T> T getMapper(Class<T> type, ReactiveSqlSession sqlSession) {
    SqlSession sqlSessionProxy = (SqlSession) Proxy.newProxyInstance(SqlSession.class.getClassLoader(), new Class[]{SqlSession.class}, new SqlSessionProxy(sqlSession));
    return super.getMapper(type, sqlSessionProxy);
  }

  public InterceptorChain getInterceptorChain(){
    try {
      Field field = Configuration.class.getDeclaredField("interceptorChain");
      InterceptorChain interceptorChain = (InterceptorChain) field.get(this.configuration);
      return interceptorChain;
    } catch (NoSuchFieldException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    return null;
  }

}
