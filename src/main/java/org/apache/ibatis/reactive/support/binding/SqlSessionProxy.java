package org.apache.ibatis.reactive.support.binding;

import org.apache.ibatis.reactive.support.session.ReactiveSqlSession;
import org.apache.ibatis.session.SqlSession;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class SqlSessionProxy implements InvocationHandler {

  private ReactiveSqlSession reactiveSqlSession;

  private final List<Method> methods = Arrays.asList(SqlSession.class.getMethods());

  public SqlSessionProxy(ReactiveSqlSession reactiveSqlSession) {
    this.reactiveSqlSession = reactiveSqlSession;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
//    if (method.getDeclaringClass().equals(Object.class)){
//      return method.invoke(proxy, args);
//    }
    if (methods.contains(method)) {
      throw new ReflectiveOperationException("not support");
    }
    return method.invoke(proxy, args);
  }

  public ReactiveSqlSession getRealSqlSession() {
    return this.reactiveSqlSession;
  }

}
