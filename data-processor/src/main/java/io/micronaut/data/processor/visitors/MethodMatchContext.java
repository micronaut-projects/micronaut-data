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
package io.micronaut.data.processor.visitors;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A match context for finding a matching method.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class MethodMatchContext extends MatchContext {

    private SourcePersistentEntity entity;
    private final Map<Element, String> parametersInRole;
    private final Function<ClassElement, SourcePersistentEntity> entityResolver;
    private final Function<String, SourcePersistentEntity> entityBySimplyNameResolver;

    /**
     * Creates the context.
     * @param queryBuilder The query builder
     * @param repositoryClass The repository class
     * @param entity The entity
     * @param visitorContext The visitor context
     * @param returnType The return type
     * @param methodElement The method element
     * @param parametersInRole Parameters that fulfill a query execution role
     * @param typeRoles The type roles
     * @param parameters The parameters
     * @param entityResolver function used to resolve entities
     * @param findInterceptors The interceptors
     */
    MethodMatchContext(
            @NonNull QueryBuilder queryBuilder,
            @NonNull ClassElement repositoryClass,
            @NonNull SourcePersistentEntity entity,
            @NonNull VisitorContext visitorContext,
            @NonNull ClassElement returnType,
            @NonNull MethodElement methodElement,
            @NonNull Map<Element, String> parametersInRole,
            @NonNull Map<String, String> typeRoles,
            @NonNull List<Map.Entry<String, String>> annotationRoles,
            @NonNull ParameterElement[] parameters,
            @NonNull Function<ClassElement, SourcePersistentEntity> entityResolver,
            @NonNull Map<ClassElement, FindInterceptorDef> findInterceptors,
            @NonNull Function<String, SourcePersistentEntity> entityBySimplyNameResolver) {
        super(queryBuilder, repositoryClass, visitorContext, methodElement, typeRoles, annotationRoles, returnType, parameters, findInterceptors);
        this.entity = entity;
        this.parametersInRole = Collections.unmodifiableMap(parametersInRole);
        this.entityResolver = entityResolver;
        this.entityBySimplyNameResolver = entityBySimplyNameResolver;
    }

    /**
     * @return The entity by a simple name resolver
     */
    public Function<String, SourcePersistentEntity> getEntityBySimplyNameResolver() {
        return entityBySimplyNameResolver;
    }

    /**
     * Check whether a parameter is available in the given role.
     * @param role The role
     * @return True if there is a parameter available in the given role
     */
    @SuppressWarnings("ConstantConditions")
    public boolean hasParameterInRole(@NonNull String role) {
        return role != null && parametersInRole.containsValue(role);
    }

    /**
     * Find the parameter in role.
     * @param role The parameter role
     * @return The parameter
     */
    @Nullable
    public Element findParameterInRole(@NonNull String role) {
        for (Map.Entry<Element, String> e : parametersInRole.entrySet()) {
            if (e.getValue().equals(role)) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * @return Parameters that fulfill a query execution role
     */
    @NonNull
    public Map<Element, String> getParametersInRole() {
        return parametersInRole;
    }

    /**
     * The root entity being queried.
     * @return The root entity
     */
    public SourcePersistentEntity getRootEntity() {
        return entity;
    }

    /**
     * @param entity he root entity being queried.
     */
    public void setRootEntity(SourcePersistentEntity entity) {
        this.entity = entity;
    }

    /**
     * Returns a list of parameters that are not fulfilling a specific query role.
     * @return The parameters not in role
     */
    public @NonNull List<ParameterElement> getParametersNotInRole() {
        return Arrays.stream(getParameters()).filter(p ->
            !this.parametersInRole.containsKey(p)
        ).toList();
    }

    /**
     * Returns a list of parameters that are not fulfilling a specific query role.
     * @return The parameters not in role
     */
    public @NonNull List<ParameterElement> getParametersInRoleList() {
        return Arrays.stream(getParameters()).filter(this.parametersInRole::containsKey).toList();
    }

    /**
     * Resolves an entity.
     * @param element The element
     * @return The entity
     */
    public @NonNull SourcePersistentEntity getEntity(@NonNull ClassElement element) {
        return entityResolver.apply(element);
    }
}
