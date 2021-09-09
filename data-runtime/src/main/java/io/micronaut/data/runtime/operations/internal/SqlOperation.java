/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.operations.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;

import java.util.Map;

/**
 * The db operation.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
public abstract class SqlOperation {

    protected String query;
    protected final Dialect dialect;

    /**
     * Creates a new instance.
     *
     * @param query   The sql query
     * @param dialect The dialect
     */
    protected SqlOperation(String query, Dialect dialect) {
        this.query = query;
        this.dialect = dialect;
    }

    /**
     * Get sql query.
     *
     * @return sql query
     */
    public String getQuery() {
        return query;
    }

    /**
     * Get dialect.
     *
     * @return dialect
     */
    public Dialect getDialect() {
        return dialect;
    }

    /**
     * Return true if query contains previous version check.
     * If true and modifying query updates less records than expected {@link io.micronaut.data.exceptions.OptimisticLockException should be thrown.}
     *
     * @return true if the query contains optimistic lock
     */
    public boolean isOptimisticLock() {
        return false;
    }

    /**
     * Collect auto-populated property values before pre-actions are triggered and property values are modified.
     *
     * @param persistentEntity The persistent entity
     * @param entity           The entity instance
     * @param <T>              The entity type
     * @return collected values
     */
    public <T> Map<String, Object> collectAutoPopulatedPreviousValues(RuntimePersistentEntity<T> persistentEntity, T entity) {
        return null;
    }

    /**
     * Set query parameters.
     *
     * @param context          The context
     * @param connection       The connection
     * @param stmt             The statement
     * @param persistentEntity The persistentEntity
     * @param entity           The entity
     * @param previousValues   The previous auto-populated collected values
     * @param <T>              The entity type
     * @param <Cnt>            The connection type
     * @param <PS>             The statement type
     */
    public abstract <T, Cnt, PS> void setParameters(OpContext<Cnt, PS> context,
                                                    Cnt connection,
                                                    PS stmt,
                                                    RuntimePersistentEntity<T> persistentEntity,
                                                    T entity,
                                                    Map<String, Object> previousValues);

}
