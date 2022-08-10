package org.apache.ibatis.r2dbc.support.executor.context;

public class R2dbcErrorContext {

  private static final String LINE_SEPARATOR = System.lineSeparator();

  private R2dbcErrorContext stored;
  private String resource;
  private String activity;
  private String object;
  private String message;
  private String sql;
  private Throwable cause;

  private R2dbcErrorContext() {
  }

  public static R2dbcErrorContext instance() {
    return new R2dbcErrorContext();
  }

//  public ErrorContext store() {
//    R2dbcErrorContext newContext = new R2dbcErrorContext();
//    newContext.stored = this;
//    return LOCAL.get();
//  }
//
//  public ErrorContext recall() {
//    if (stored != null) {
//      LOCAL.set(stored);
//      stored = null;
//    }
//    return LOCAL.get();
//  }

  public R2dbcErrorContext resource(String resource) {
    this.resource = resource;
    return this;
  }

  public R2dbcErrorContext activity(String activity) {
    this.activity = activity;
    return this;
  }

  public R2dbcErrorContext object(String object) {
    this.object = object;
    return this;
  }

  public R2dbcErrorContext message(String message) {
    this.message = message;
    return this;
  }

  public R2dbcErrorContext sql(String sql) {
    this.sql = sql;
    return this;
  }

  public R2dbcErrorContext cause(Throwable cause) {
    this.cause = cause;
    return this;
  }

  public R2dbcErrorContext reset() {
    resource = null;
    activity = null;
    object = null;
    message = null;
    sql = null;
    cause = null;
//    LOCAL.remove();
    return this;
  }

  @Override
  public String toString() {
    StringBuilder description = new StringBuilder();

    // message
    if (this.message != null) {
      description.append(LINE_SEPARATOR);
      description.append("### ");
      description.append(this.message);
    }

    // resource
    if (resource != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error may exist in ");
      description.append(resource);
    }

    // object
    if (object != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error may involve ");
      description.append(object);
    }

    // activity
    if (activity != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error occurred while ");
      description.append(activity);
    }

    // sql
    if (sql != null) {
      description.append(LINE_SEPARATOR);
      description.append("### SQL: ");
      description.append(sql.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim());
    }

    // cause
    if (cause != null) {
      description.append(LINE_SEPARATOR);
      description.append("### Cause: ");
      description.append(cause.toString());
    }

    return description.toString();
  }

}
