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
