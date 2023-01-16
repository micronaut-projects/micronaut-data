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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.TypedElement;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * The method info. This class describes the pre-computed method handling for a
 * repository and is computed into a {@link io.micronaut.data.intercept.annotation.DataMethod} annotation
 * which is readable at runtime.
 *
 * @author graemerocher
 * @since 1.0
 */
public final class MethodMatchInfo {

    private final DataMethod.OperationType operationType;
    private final TypedElement resultType;
    private final ClassElement interceptor;

    private final Map<String, String> parameterRoles = new HashMap<>(2);
    private boolean dto;
    private boolean optimisticLock;

    private QueryResult queryResult;
    private QueryResult countQueryResult;
    private boolean isRawQuery;
    private boolean encodeEntityParameters;

    /**
     * Creates a method info.
     *
     * @param operationType The operation type
     * @param resultType    The result type, can be null for void etc.
     * @param interceptor   The interceptor type to execute at runtime
     */
    public MethodMatchInfo(DataMethod.OperationType operationType, @Nullable TypedElement resultType, @Nullable ClassElement interceptor) {
        this.operationType = operationType;
        this.interceptor = interceptor;
        this.resultType = resultType;
    }

    /**
     * @return The operation type
     */
    public DataMethod.OperationType getOperationType() {
        return operationType;
    }

    /**
     * Is the query result a DTO query.
     * @return True if it is
     */
    public boolean isDto() {
        return dto;
    }

    /**
     * Gets optimistic lock value.
     *
     * @return the value
     */
    public boolean isOptimisticLock() {
        return optimisticLock;
    }

    /**
     * Sets optimistic lock value.
     *
     * @param optimisticLock new value
     */
    public void setOptimisticLock(boolean optimisticLock) {
        this.optimisticLock = optimisticLock;
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
     * The computed result type.
     * @return The result type.
     */
    @Nullable public TypedElement getResultType() {
        return resultType;
    }

    /**
     * The runtime interceptor that will handle the method.
     * @return The runtime interceptor
     */
    @Nullable public ClassElement getRuntimeInterceptor() {
        return interceptor;
    }

    public MethodMatchInfo dto(boolean dto) {
        this.dto = dto;
        return this;
    }

    public MethodMatchInfo queryResult(QueryResult queryResult) {
        this.queryResult = queryResult;
        return this;
    }

    public MethodMatchInfo countQueryResult(QueryResult countQueryResult) {
        this.countQueryResult = countQueryResult;
        return this;
    }

    public MethodMatchInfo isRawQuery(boolean isRawQuery) {
        this.isRawQuery = isRawQuery;
        return this;
    }

    public MethodMatchInfo encodeEntityParameters(boolean encodeEntityParameters) {
        this.encodeEntityParameters = encodeEntityParameters;
        return this;
    }

    public MethodMatchInfo optimisticLock(boolean optimisticLock) {
        this.optimisticLock = optimisticLock;
        return this;
    }

    public ClassElement getInterceptor() {
        return interceptor;
    }

    public QueryResult getQueryResult() {
        return queryResult;
    }

    public QueryResult getCountQueryResult() {
        return countQueryResult;
    }

    public boolean isRawQuery() {
        return isRawQuery;
    }

    public boolean isEncodeEntityParameters() {
        return encodeEntityParameters;
    }

}
