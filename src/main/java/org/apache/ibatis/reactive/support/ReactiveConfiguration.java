package org.apache.ibatis.reactive.support;

import org.apache.ibatis.session.Configuration;

public class ReactiveConfiguration extends Configuration {

  private Configuration configuration;


  public Configuration getConfiguration() {
    return configuration;
  }
  public void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

}
