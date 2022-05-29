/*
 *    Copyright 2009-2022 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reactive.support.executor.type;

import org.apache.ibatis.reactive.support.executor.result.RowResultWrapper;
import org.apache.ibatis.type.TypeHandler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.sql.CallableStatement;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Stream;

public class R2dbcTypeHandler implements InvocationHandler {

  private final static HashMap<Class,R2dbcTypeHandler> cacheR2dbcTypeHandler = new HashMap<>();
  private TypeHandler delegatedTypeHandler;
  private RowResultWrapper rowResultWrapper;
  private Class<?> typeHandlerArgumentType;

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if ("contextWith".equals(method.getName())) {
      this.delegatedTypeHandler = (TypeHandler) args[0];
      this.rowResultWrapper = (RowResultWrapper) args[1];
      this.typeHandlerArgumentType = this.getTypeHandlerArgumentType(delegatedTypeHandler).orElse(Object.class);
      return null;
    }
    //not getResult() method ,return original invocation
    if (!"getResult".equals(method.getName())) {
      return method.invoke(delegatedTypeHandler, args);
    }
    Object firstArg = args[0];
    Object secondArg = args[1];
    if (null == secondArg) {
      return method.invoke(delegatedTypeHandler, args);
    }
    if (firstArg instanceof CallableStatement) {
      return method.invoke(delegatedTypeHandler, args);
    }
    //not supported
//    if (notSupportedDataTypes.contains(this.typeHandlerArgumentType)) {
//      throw new IllegalArgumentException("Unsupported Result Data type : " + typeHandlerArgumentType);
//    }
//    //using adapter
//    if (r2dbcTypeHandlerAdapters.containsKey(this.typeHandlerArgumentType)) {
//      log.debug("Found r2dbc type handler adapter fro result type : " + this.typeHandlerArgumentType);
//      R2dbcTypeHandlerAdapter r2dbcTypeHandlerAdapter = r2dbcTypeHandlerAdapters.get(this.typeHandlerArgumentType);
//      // T getResult(ResultSet rs, String columnName)
//      if (secondArg instanceof String) {
//        return r2dbcTypeHandlerAdapter.getResult(rowResultWrapper.getRow(), rowResultWrapper.getRowMetadata(), (String) secondArg);
//      }
//      // T getResult(ResultSet rs, int columnIndex)
//      if (secondArg instanceof Integer) {
//        return r2dbcTypeHandlerAdapter.getResult(rowResultWrapper.getRow(), rowResultWrapper.getRowMetadata(), (Integer) secondArg - 1);
//      }
//    }
    // T getResult(ResultSet rs, String columnName)
    if (secondArg instanceof String) {
      return rowResultWrapper.getRow().get((String) secondArg, typeHandlerArgumentType);
    }
    // T getResult(ResultSet rs, int columnIndex)
    if (secondArg instanceof Integer) {
      return rowResultWrapper.getRow().get((Integer) secondArg - 1, typeHandlerArgumentType);
    }
    return null;
  }

  private Optional<Class> getTypeHandlerArgumentType(TypeHandler typeHandler) {
    return Stream.of(typeHandler.getClass().getGenericSuperclass())
      .filter(type -> type instanceof ParameterizedType)
      .map(ParameterizedType.class::cast)
      .filter(parameterizedType -> TypeHandler.class.isAssignableFrom((Class) (parameterizedType.getRawType())))
      .flatMap(parameterizedType -> Stream.of(parameterizedType.getActualTypeArguments()))
      .map(Class.class::cast)
      .findFirst();
  }
}
