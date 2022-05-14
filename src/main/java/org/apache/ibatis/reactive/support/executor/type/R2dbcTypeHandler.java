package org.apache.ibatis.reactive.support.executor.type;

import org.apache.ibatis.reactive.support.executor.result.RowResultWrapper;
import org.apache.ibatis.type.TypeHandler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;

public class R2dbcTypeHandler implements InvocationHandler {

  private final static HashMap<Class,R2dbcTypeHandler> cacheR2dbcTypeHandler = new HashMap<>();
  private TypeHandler delegatedTypeHandler;
  private RowResultWrapper rowResultWrapper;
  private Class<?> typeHandlerArgumentType;

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    return null;
  }

}
