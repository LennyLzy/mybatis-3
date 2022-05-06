package org.apache.ibatis.reactive.support.session;

import org.apache.ibatis.reactive.support.ReactiveConfiguration;
import reactor.core.publisher.Mono;

public interface ReactiveSqlSessionFactory {

  ReactiveSqlSession openSession();

  ReactiveConfiguration getConfiguration();

}
