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
