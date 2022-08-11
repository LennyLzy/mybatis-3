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

import io.r2dbc.spi.IsolationLevel;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reactive.support.ReactiveConfiguration;
import org.apache.ibatis.reactive.support.executor.DefaultReactiveExecutor;
import org.apache.ibatis.reactive.support.executor.ReactiveExecutor;
import org.apache.ibatis.reactive.support.executor.support.R2dbcStatementLog;
import org.apache.ibatis.reactive.support.executor.support.ReactiveExecutorContext;
import org.apache.ibatis.reflection.ParamNameResolver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Optional;

public class DefaultReactiveSqlSession implements ReactiveSqlSession, MybatisReactiveContextManager {

  private ReactiveConfiguration configuration;
  private IsolationLevel isolationLevel;
  private Boolean autoCommit;
  private final ReactiveExecutor executor;
  private boolean withTransaction = false;


  public DefaultReactiveSqlSession(ReactiveConfiguration configuration, IsolationLevel isolationLevel, Boolean autoCommit, ReactiveExecutor executor) {
    this.configuration = configuration;
    this.isolationLevel = isolationLevel;
    this.autoCommit = autoCommit;
    this.withTransaction = !autoCommit;
    this.executor = executor;
  }

  @Override
  public <T> Mono<T> selectOne(String statement) {
    return this.selectOne(statement, null);
  }

  @Override
  public <T> Mono<T> selectOne(String statement, Object parameter) {
    return this.<T>selectList(statement, parameter)
      .buffer(2)
      .flatMap(results -> {
        if (results.isEmpty()) {
          return Mono.empty();
        }
        if (results.size() > 1) {
          return Mono.error(new TooManyResultsException("Expected one result (or null) to be returned by selectOne()"));
        }
        return Mono.justOrEmpty(results.get(0));
      }).singleOrEmpty();
  }

  @Override
  public <T> Flux<T> selectList(String statement, Object parameter) {
    MappedStatement mappedStatement = configuration.getMappedStatement(statement);
    Object wrappedParameter = ParamNameResolver.wrapToMapIfCollection(parameter, null);
    return executor.<T>query(mappedStatement, wrappedParameter)
      .contextWrite(context -> initReactiveExecutorContext(context, this.configuration.getR2dbcStatementLog(mappedStatement)));
  }

  @Override
  public Mono<Integer> update(String statement, Object parameter) {
    MappedStatement mappedStatement = configuration.getMappedStatement(statement);
    Object wrappedParameter = ParamNameResolver.wrapToMapIfCollection(parameter, null);
    return executor.update(mappedStatement, wrappedParameter)
      .contextWrite(context -> initReactiveExecutorContext(context, this.configuration.getR2dbcStatementLog(mappedStatement)));
  }

  @Override
  public <T> Flux<T> selectList(String statement) {
    return this.selectList(statement, null);
  }

  @Override
  public Mono<Integer> insert(String statement) {
    return this.insert(statement, null);
  }

  @Override
  public Mono<Integer> insert(String statement, Object parameter) {
    return this.update(statement, parameter);
  }

  @Override
  public Mono<Integer> update(String statement) {
    return this.update(statement, null);
  }

  @Override
  public Mono<Integer> delete(String statement) {
    return this.delete(statement, null);
  }

  @Override
  public Mono<Integer> delete(String statement, Object parameter) {
    return this.update(statement, parameter);
  }

  @Override
  public Mono<Void> commit() {
    return this.commit(false);
  }

  @Override
  public Mono<Void> commit(boolean force) {
    return executor.commit(force);
  }

  @Override
  public Mono<Void> rollback() {
    return this.rollback(false);
  }

  @Override
  public Mono<Void> rollback(boolean force) {
    return executor.rollback(force);
  }

  @Override
  public Mono<Void> close() {
    return this.close(false);
  }

  public Mono<Void> close(boolean force) {
    return executor.close(force);
  }

  @Override
  public Mono<Void> clearCache() {
    return null;
  }

  @Override
  public ReactiveConfiguration getConfiguration() {
    return this.configuration;
  }

  @Override
  public <T> T getMapper(Class<T> type) {
    return this.configuration.getMapper(type, this);
  }

  @Override
  public Context initReactiveExecutorContext(Context context, R2dbcStatementLog r2dbcStatementLog) {
    Optional<ReactiveExecutorContext> optionalContext = context.getOrEmpty(ReactiveExecutorContext.class)
      .map(ReactiveExecutorContext.class::cast);
    if (optionalContext.isPresent()) {
      ReactiveExecutorContext reactiveExecutorContext = optionalContext.get();
      if (this.withTransaction) {
        reactiveExecutorContext.setWithTransaction();
      }
      reactiveExecutorContext.setR2dbcStatementLog(r2dbcStatementLog);
      return context;
    }
    ReactiveExecutorContext newContext = new ReactiveExecutorContext(autoCommit, isolationLevel);
    newContext.setR2dbcStatementLog(r2dbcStatementLog);
    if (this.withTransaction) {
      newContext.setWithTransaction();
    }
    return context.put(ReactiveExecutorContext.class, newContext);
  }
}
