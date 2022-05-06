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

/**
 * The interface Type handle context.
 *
 * @author Gang Cheng
 * @version 1.0.0
 */
public interface TypeHandleContext {

    /**
     * set delegated type handler
     *
     * @param delegatedTypeHandler the delegated type handler
     * @param rowResultWrapper     the row result wrapper
     */
    void contextWith(TypeHandler delegatedTypeHandler, RowResultWrapper rowResultWrapper);

}
