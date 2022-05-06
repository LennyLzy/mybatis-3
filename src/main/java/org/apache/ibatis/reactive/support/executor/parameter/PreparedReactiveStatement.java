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
package org.apache.ibatis.reactive.support.executor.parameter;

import io.r2dbc.spi.Statement;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.reactive.support.ReactiveConfiguration;
import org.apache.ibatis.reactive.support.session.ReactiveSqlSession;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;

public class PreparedReactiveStatement implements InvocationHandler {

  private Statement statement;

  public PreparedReactiveStatement(Statement statement) {
    this.statement = statement;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (Object.class.equals(method.getDeclaringClass())) {
      return method.invoke(proxy, args);
    }
    if (PreparedStatement.class.equals(method.getDeclaringClass()) && !method.getName().startsWith("set")) {
      return method.invoke(proxy, args);
    }
    if (PreparedStatement.class.equals(method.getDeclaringClass()) && method.getName().startsWith("setNull")) {
      bindNullSourceStatement((int) args[0], args[1]);
    }
    if (PreparedStatement.class.equals(method.getDeclaringClass()) && method.getName().startsWith("set")) {
      bindSourceStatement((int) args[0], args[1]);
    }
    if (PreparedStatement.class.equals(method.getDeclaringClass()))
      return null;
    return method.invoke(proxy, args);
  }

  private void bindNullSourceStatement(int index, Object value) {
    this.statement.bindNull(index - 1, value.getClass());
  }

  private void bindSourceStatement(int index, Object value) {
    this.statement.bind(index - 1, value);
  }

  public Statement getSourceStatement() {
    return this.statement;
  }

}
