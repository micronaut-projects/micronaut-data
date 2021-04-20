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

import io.micronaut.core.annotation.NonNull;
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
import java.util.stream.Collectors;

/**
 * A match context for finding a matching method.
 *
 * @author graemerocher
 * @since 1.0
 */
public class MethodMatchContext extends MatchContext {

    @NonNull
    private final SourcePersistentEntity entity;
    private final Map<String, Element> parametersInRole;
    private final Function<ClassElement, SourcePersistentEntity> entityResolver;

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
     */
    MethodMatchContext(
            @NonNull QueryBuilder queryBuilder,
            @NonNull ClassElement repositoryClass,
            @NonNull SourcePersistentEntity entity,
            @NonNull VisitorContext visitorContext,
            @NonNull ClassElement returnType,
            @NonNull MethodElement methodElement,
            @NonNull Map<String, Element> parametersInRole,
            @NonNull Map<String, String> typeRoles,
            @NonNull ParameterElement[] parameters,
            @NonNull Function<ClassElement, SourcePersistentEntity> entityResolver) {
        super(queryBuilder, repositoryClass, visitorContext, methodElement, typeRoles, returnType, parameters);
        this.entity = entity;
        this.parametersInRole = Collections.unmodifiableMap(parametersInRole);
        this.entityResolver = entityResolver;
    }

    /**
     * Check whether a parameter is available in the given role.
     * @param role The role
     * @return True if there is a parameter available in the given role
     */
    @SuppressWarnings("ConstantConditions")
    public boolean hasParameterInRole(@NonNull String role) {
        return role != null && parametersInRole.containsKey(role);
    }

    /**
     * @return Parameters that fulfill a query execution role
     */
    @NonNull
    public Map<String, Element> getParametersInRole() {
        return parametersInRole;
    }

    /**
     * The root entity being queried.
     * @return The root entity
     */
    @NonNull
    public SourcePersistentEntity getRootEntity() {
        return entity;
    }

    /**
     * Returns a list of parameters that are not fulfilling a specific query role.
     * @return The parameters not in role
     */
    public @NonNull List<ParameterElement> getParametersNotInRole() {
        return Arrays.stream(getParameters()).filter(p ->
            !this.parametersInRole.containsValue(p)
        ).collect(Collectors.toList());
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
