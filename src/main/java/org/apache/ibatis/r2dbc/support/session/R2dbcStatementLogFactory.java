package org.apache.ibatis.r2dbc.support.session;

import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reactive.support.ReactiveConfiguration;
import org.apache.ibatis.reactive.support.executor.support.R2dbcStatementLog;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class R2dbcStatementLogFactory {

  private final Map<String,R2dbcStatementLog> r2dbcStatementLogContainer = new HashMap<>();
  private final R2dbcConfiguration configuration;

  /**
   * Instantiates a new r2dbc statement log factory.
   *
   * @param r2dbcMybatisConfiguration the r2dbc mybatis configuration
   */
  public R2dbcStatementLogFactory(R2dbcConfiguration r2dbcMybatisConfiguration) {
    this.configuration = r2dbcMybatisConfiguration;
  }

  /**
   * Init r2dbc statement log.
   *
   * @param mappedStatement the mapped statement
   */
  public void initR2dbcStatementLog(MappedStatement mappedStatement){
    String logId = mappedStatement.getId();
    if (configuration.getLogPrefix() != null) {
      logId = configuration.getLogPrefix() + mappedStatement.getId();
    }
    r2dbcStatementLogContainer.put(logId,new R2dbcStatementLog(mappedStatement.getStatementLog()));
  }

  /**
   * Get r2dbc statement log optional.
   *
   * @param mappedStatement the MappedStatement
   * @return the R2dbcStatementLog
   */
  public R2dbcStatementLog getR2dbcStatementLog(MappedStatement mappedStatement){
    String logId = mappedStatement.getId();
    if (configuration.getLogPrefix() != null) {
      logId = configuration.getLogPrefix() + mappedStatement.getId();
    }
    R2dbcStatementLog r2dbcStatementLog = r2dbcStatementLogContainer.get(logId);
    if(Objects.nonNull(r2dbcStatementLog)){
      return r2dbcStatementLog;
    }
    r2dbcStatementLog = new R2dbcStatementLog(mappedStatement.getStatementLog());
    this.r2dbcStatementLogContainer.put(logId,r2dbcStatementLog);
    return r2dbcStatementLog;
  }

  /**
   * get all r2dbc statement logs
   * @return unmodifiable {@code Map<String,R2dbcStatementLog>}
   */
  public Map<String,R2dbcStatementLog> getAllR2dbcStatementLog(){
    return Collections.unmodifiableMap(this.r2dbcStatementLogContainer);
  }

}
