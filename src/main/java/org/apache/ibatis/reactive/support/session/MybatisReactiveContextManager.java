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

import org.apache.ibatis.reactive.support.executor.support.R2dbcStatementLog;
import org.apache.ibatis.reactive.support.executor.support.ReactiveExecutorContext;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public interface MybatisReactiveContextManager {

  static Mono<ReactiveExecutorContext> currentContext() {
    return Mono.deferContextual(contextView -> Mono
      .justOrEmpty(contextView.getOrEmpty(ReactiveExecutorContext.class))
      .switchIfEmpty(Mono.error(new IllegalStateException("ReactiveExecutorContext is empty")))
      .cast(ReactiveExecutorContext.class)
    );
  }

  Context initReactiveExecutorContext(Context context, R2dbcStatementLog r2dbcStatementLog);

}
