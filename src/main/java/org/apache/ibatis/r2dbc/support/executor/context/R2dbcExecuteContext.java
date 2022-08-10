package org.apache.ibatis.r2dbc.support.executor.context;


import org.apache.ibatis.r2dbc.support.session.R2dbcStatementLog;

public class R2dbcExecuteContext {

  private R2dbcStatementLog r2dbcStatementLog;

  private R2dbcErrorContext r2dbcErrorContext;

  public R2dbcExecuteContext(R2dbcStatementLog r2dbcStatementLog, R2dbcErrorContext r2dbcErrorContext) {
    this.r2dbcStatementLog = r2dbcStatementLog;
    this.r2dbcErrorContext = r2dbcErrorContext;
  }

  public R2dbcStatementLog getR2dbcStatementLog() {
    return r2dbcStatementLog;
  }

  public void setR2dbcStatementLog(R2dbcStatementLog r2dbcStatementLog) {
    this.r2dbcStatementLog = r2dbcStatementLog;
  }

  public R2dbcErrorContext getR2dbcErrorContext() {
    return r2dbcErrorContext;
  }

  public void setR2dbcErrorContext(R2dbcErrorContext r2dbcErrorContext) {
    this.r2dbcErrorContext = r2dbcErrorContext;
  }
}
