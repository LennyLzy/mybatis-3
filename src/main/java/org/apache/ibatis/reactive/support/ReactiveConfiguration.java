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
import io.r2dbc.spi.Wrapped;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.plugin.InterceptorChain;
import org.apache.ibatis.reactive.support.binding.SqlSessionProxy;
import org.apache.ibatis.reactive.support.executor.support.R2dbcStatementLog;
import org.apache.ibatis.reactive.support.executor.support.R2dbcStatementLogFactory;
import org.apache.ibatis.reactive.support.session.ReactiveSqlSession;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;


public class ReactiveConfiguration implements Wrapped<Configuration> {

  private Configuration configuration;
  private final R2dbcStatementLogFactory r2dbcStatementLogFactory = new R2dbcStatementLogFactory(this);
  private final ConnectionFactory connectionFactory;
  private volatile InterceptorChain interceptorChain;
  protected ObjectFactory objectFactory = new DefaultObjectFactory();

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
    return this.configuration.getMapper(type, sqlSessionProxy);
  }

  public InterceptorChain getInterceptorChain() {
    if (this.interceptorChain == null) {
      synchronized (this) {
        if (this.interceptorChain == null) {
          this.interceptorChain = new InterceptorChain();
          this.configuration.getInterceptors().forEach(interceptor -> this.interceptorChain.addInterceptor(interceptor));
        }
      }
    }
    return this.interceptorChain;
  }

  @Override
  public Configuration unwrap() {
    return this.configuration;
  }

  public MappedStatement getMappedStatement(String name) {
    return this.configuration.getMappedStatement(name);
  }

  public String getLogPrefix() {
    return this.configuration.getLogPrefix();
  }

  public TypeHandlerRegistry getTypeHandlerRegistry() {
    return this.configuration.getTypeHandlerRegistry();
  }

  public ObjectFactory getObjectFactory() {
    return this.objectFactory;
  }

  public ReflectorFactory getReflectorFactory() {
    return this.configuration.getReflectorFactory();
  }

  public ResultMap getResultMap(String nestedResultMapId) {
    return this.configuration.getResultMap(nestedResultMapId);
  }

  public <T> void addMapper(Class<T> type) {
    this.getConfiguration().addMapper(type);
  }

  public boolean hasMapper(Class<?> type) {
    return this.getConfiguration().hasMapper(type);
  }

}
