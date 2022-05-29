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
