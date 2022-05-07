package org.apache.ibatis.reactive.support.executor;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.IsolationLevel;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reactive.support.ReactiveConfiguration;
import org.apache.ibatis.session.RowBounds;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DefaultReactiveExecutor extends BaseReactiveExecutor {

  private IsolationLevel isolationLevel;

  private Boolean autoCommit;

  public DefaultReactiveExecutor(ReactiveConfiguration configuration, IsolationLevel isolationLevel, Boolean autoCommit) {
    super(configuration);
    this.isolationLevel = isolationLevel;
    this.autoCommit = autoCommit;
  }

  @Override
  protected Mono<Integer> doUpdateWithConnection(Connection connection, MappedStatement mappedStatement, Object parameter) {
    return null;
  }

  @Override
  protected <T> Flux<T> doQueryWithConnection(Connection connection, MappedStatement mappedStatement, Object parameter) {
    return null;
  }
}
