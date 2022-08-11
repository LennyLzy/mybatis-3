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
package org.apache.ibatis.reactive.support.executor.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reactive executor context attribute
 *
 * @author Gang Cheng
 * @version 1.0.5
 * @since 1.0.5
 */
public class ReactiveExecutorContextAttribute {

    private final Map<String,Object> attribute = new ConcurrentHashMap<>();

    /**
     * Gets attribute.
     *
     * @return the attribute
     */
    public Map<String, Object> getAttribute() {
        return attribute;
    }
}