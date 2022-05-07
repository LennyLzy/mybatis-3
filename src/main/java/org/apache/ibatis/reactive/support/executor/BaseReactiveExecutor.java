package org.apache.ibatis.reactive.support.executor;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reactive.support.ReactiveConfiguration;
import org.apache.ibatis.reactive.support.connection.ConnectionCloseHolder;
import org.apache.ibatis.reactive.support.session.MybatisReactiveContextManager;
import org.apache.ibatis.session.RowBounds;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public abstract class BaseReactiveExecutor implements ReactiveExecutor {

  private static final Log log = LogFactory.getLog(BaseReactiveExecutor.class);

  protected final ReactiveConfiguration configuration;

  public BaseReactiveExecutor(ReactiveConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public Mono<Integer> update(MappedStatement mappedStatement, Object parameter) {
    return MybatisReactiveContextManager.currentContext()
      .flatMap(reactiveExecutorContext -> {
        reactiveExecutorContext.setDirty();
        return this.inConnection(
          this.configuration.getConnectionFactory(),
          connection -> this.doUpdateWithConnection(connection, mappedStatement, parameter)
        );
      });
  }

  @Override
  public <T> Flux<T> query(MappedStatement mappedStatement, Object parameter) {
    return this.inConnectionMany(
      this.configuration.getConnectionFactory(),
      connection -> this.doQueryWithConnection(connection, mappedStatement, parameter)
    );
  }

  @Override
  public Mono<Void> commit(boolean required) {
    return MybatisReactiveContextManager.currentContext()
      .flatMap(reactiveExecutorContext -> {
        reactiveExecutorContext.setForceCommit(reactiveExecutorContext.isDirty() || required);
        return Mono.justOrEmpty(reactiveExecutorContext.getConnection())
          .flatMap(connection -> Mono.from(connection.close()))
          .then(Mono.defer(() -> {
            reactiveExecutorContext.resetDirty();
            return Mono.empty();
          }));
      });
  }

  @Override
  public Mono<Void> rollback(boolean required) {
    return MybatisReactiveContextManager.currentContext()
      .flatMap(reactiveExecutorContext -> {
        reactiveExecutorContext.setForceRollback(reactiveExecutorContext.isDirty() || required);
        return Mono.justOrEmpty(reactiveExecutorContext.getConnection())
          .flatMap(connection -> Mono.from(connection.close()))
          .then(Mono.defer(() -> {
            reactiveExecutorContext.resetDirty();
            return Mono.empty();
          }));
      });
  }

  @Override
  public Mono<Void> close(boolean forceRollback) {
    return MybatisReactiveContextManager.currentContext()
      .flatMap(reactiveExecutorContext -> {
        reactiveExecutorContext.setForceRollback(forceRollback);
        reactiveExecutorContext.setRequireClosed(true);
        return Mono.justOrEmpty(reactiveExecutorContext.getConnection())
          .flatMap(connection -> Mono.from(connection.close()))
          .then(Mono.defer(() -> {
            reactiveExecutorContext.resetDirty();
            return Mono.empty();
          }));
      });
  }

  /**
   * do update with connection actually
   *
   * @param connection      the connection
   * @param mappedStatement the mapped statement
   * @param parameter       the parameter
   * @return mono
   */
  protected abstract Mono<Integer> doUpdateWithConnection(Connection connection, MappedStatement mappedStatement, Object parameter);

  /**
   * do query with connection actually
   *
   * @param <E>             the type parameter
   * @param connection      the connection
   * @param mappedStatement the mapped statement
   * @param parameter       the parameter
   * @return flux
   */
  protected abstract <E> Flux<E> doQueryWithConnection(Connection connection, MappedStatement mappedStatement, Object parameter);

  /**
   * in connection
   *
   * @param <T>               the type parameter
   * @param connectionFactory the connection factory
   * @param action            the action
   * @return mono
   */
  protected <T> Mono<T> inConnection(ConnectionFactory connectionFactory, Function<Connection, Mono<T>> action) {
    Mono<ConnectionCloseHolder> connectionMono = MybatisReactiveContextManager.currentContext()
      .flatMap(reactiveExecutorContext -> Mono
        .from(connectionFactory.create())
        .doOnNext(connection -> log.debug("Execute Statement With Mono,Get Connection [" + connection + "] From Connection Factory "))
      )
      .map(connection -> new ConnectionCloseHolder(connection, this::closeConnection));
    // ensure close method only execute once with Mono.usingWhen() operator
    return Mono.usingWhen(connectionMono,
      connection -> action.apply(connection.getTarget()),
      ConnectionCloseHolder::close,
      (connection, err) -> connection.close(),
      ConnectionCloseHolder::close);
  }

  /**
   * in connection many
   *
   * @param <T>               the type parameter
   * @param connectionFactory the connection factory
   * @param action            the action
   * @return flux
   */
  protected <T> Flux<T> inConnectionMany(ConnectionFactory connectionFactory, Function<Connection, Flux<T>> action) {
    Mono<ConnectionCloseHolder> connectionMono = MybatisReactiveContextManager.currentContext()
      .flatMap(reactiveExecutorContext -> Mono
        .from(connectionFactory.create())
        .doOnNext(connection -> log.debug("Execute Statement With Flux,Get Connection [" + connection + "] From Connection Factory "))
      )
      .map(connection -> new ConnectionCloseHolder(connection, this::closeConnection));
    // ensure close method only execute once with Mono.usingWhen() operator
    return Flux.usingWhen(connectionMono,
      connection -> action.apply(connection.getTarget()),
      ConnectionCloseHolder::close,
      (connection, err) -> connection.close(),
      ConnectionCloseHolder::close);
  }

  /**
   * Release the {@link Connection}.
   *
   * @param connection the connection
   * @return mono
   */
  protected Mono<Void> closeConnection(Connection connection) {
    return Mono.from(connection.close())
      .onErrorResume(e -> Mono.from(connection.close()));
  }

}
