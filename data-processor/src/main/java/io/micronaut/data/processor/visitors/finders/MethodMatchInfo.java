/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.TypedElement;

import java.util.*;


/**
 * The method info. This class describes the pre-computed method handling for a
 * repository and is computed into a {@link io.micronaut.data.intercept.annotation.DataMethod} annotation
 * which is readable at runtime.
 *
 * @author graemerocher
 * @since 1.0
 */
public class MethodMatchInfo {

    private final TypedElement resultType;
    private final QueryModel query;
    private final ClassElement interceptor;
    private final OperationType operationType;
    private final String[] updateProperties;
    private final String[] autoPopulateForUpdateProperties;

    private Map<String, String> parameterRoles = new HashMap<>(2);
    private boolean dto;

    /**
     * Creates a method info.
     * @param resultType The result type
     * @param query The query
     * @param interceptor The interceptor type to execute at runtime
     */
    public MethodMatchInfo(
            @Nullable TypedElement resultType,
            @Nullable QueryModel query,
            @Nullable ClassElement interceptor) {
        this(resultType, query, interceptor, OperationType.QUERY);
    }

    /**
     * Creates a method info.
     * @param resultType The result type
     * @param query The query
     * @param interceptor The interceptor type to execute at runtime
     * @param dto indicate that this is a DTO query
     */
    public MethodMatchInfo(
            @Nullable TypedElement resultType,
            @Nullable QueryModel query,
            @Nullable ClassElement interceptor,
            boolean dto) {
        this(resultType, query, interceptor, OperationType.QUERY);
        this.dto = dto;
    }

    /**
     * Creates a method info.
     * @param resultType The result type, can be null for void etc.
     * @param query The query, can be null for interceptors that don't execute queries.
     * @param interceptor The interceptor type to execute at runtime
     * @param operationType The operation type
     */
    public MethodMatchInfo(
            @Nullable TypedElement resultType,
            @Nullable QueryModel query,
            @Nullable ClassElement interceptor,
            @NonNull OperationType operationType) {
        this(resultType, query, interceptor, operationType, null, null);
    }

    /**
     * Creates a method info.
     * @param resultType The result type, can be null for void etc.
     * @param query The query, can be null for interceptors that don't execute queries.
     * @param interceptor The interceptor type to execute at runtime
     * @param operationType The operation type
     * @param updateProperties the update properties
     * @param autoPopulateForUpdateProperties the auto-populate properties
     */
    public MethodMatchInfo(
            @Nullable TypedElement resultType,
            @Nullable QueryModel query,
            @Nullable ClassElement interceptor,
            @NonNull OperationType operationType,
            String[] updateProperties,
            String[] autoPopulateForUpdateProperties) {
        this.query = query;
        this.interceptor = interceptor;
        this.operationType = operationType;
        this.resultType = resultType;
        this.updateProperties = updateProperties;
        this.autoPopulateForUpdateProperties = autoPopulateForUpdateProperties;
    }

    /**
     * Is the query result a DTO query.
     * @return True if it is
     */
    public boolean isDto() {
        return dto;
    }

    /**
     * Adds a parameter role. This indicates that a parameter is involved
     * somehow in the query.
     * @param role The role name
     * @param name The parameter
     * @see io.micronaut.data.annotation.TypeRole
     */
    public void addParameterRole(CharSequence role, String name) {
        parameterRoles.put(role.toString(), name);
    }

    /**
     * @return The parameter roles
     */
    public Map<String, String> getParameterRoles() {
        return Collections.unmodifiableMap(parameterRoles);
    }

    /**
     * @return The properties to update for an update operation.
     */
    @NonNull public List<String> getUpdateProperties() {
        if (updateProperties == null) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(updateProperties);
        }
    }

    /**
     * @return The properties to auto-populate for an update operation.
     */
    @NonNull public List<String> getAutoPopulateForUpdateProperties() {
        if (autoPopulateForUpdateProperties == null) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(autoPopulateForUpdateProperties);
        }
    }

    /**
     * The computed result type.
     * @return The result type.
     */
    @Nullable public TypedElement getResultType() {
        return resultType;
    }

    /**
     * The query to be executed.
     * @return The query
     */
    @Nullable
    public QueryModel getQuery() {
        return query;
    }

    /**
     * The runtime interceptor that will handle the method.
     * @return The runtime interceptor
     */
    @Nullable public ClassElement getRuntimeInterceptor() {
        return interceptor;
    }

    /**
     * The operation type to execute.
     * @return The operation type
     */
    @NonNull
    public OperationType getOperationType() {
        return operationType;
    }


    /**
     * Describes the operation type.
     */
    public enum OperationType {
        /**
         * A query operation.
         */
        QUERY,
        /**
         * An update operation.
         */
        UPDATE,
        /**
         * A delete operation.
         */
        DELETE,
        /**
         * An insert operation.
         */
        INSERT
    }

}
