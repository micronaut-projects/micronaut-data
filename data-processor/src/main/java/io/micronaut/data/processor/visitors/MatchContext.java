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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A match context when matching methods.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MatchContext implements AnnotationMetadataProvider {
    @NonNull
    protected final VisitorContext visitorContext;
    @NonNull
    protected final MethodElement methodElement;
    protected final Map<String, String> typeRoles;
    protected final ClassElement returnType;
    @NonNull
    protected final ParameterElement[] parameters;
    private final ClassElement repositoryClass;
    private final QueryBuilder queryBuilder;
    private boolean failing = false;

    /**
     * Default constructor.
     * @param queryBuilder The query builder
     * @param repositoryClass The repository class
     * @param visitorContext The visitor context
     * @param methodElement The method element
     * @param typeRoles The type roles
     * @param returnType The return type
     * @param parameters The parameters
     */
    MatchContext(
            @NonNull QueryBuilder queryBuilder,
            @NonNull ClassElement repositoryClass,
            @NonNull VisitorContext visitorContext,
            @NonNull MethodElement methodElement,
            @NonNull Map<String, String> typeRoles,
            @NonNull ClassElement returnType,
            @NonNull ParameterElement[] parameters) {
        this.queryBuilder = queryBuilder;
        this.repositoryClass = repositoryClass;
        this.visitorContext = visitorContext;
        this.methodElement = methodElement;
        this.typeRoles = typeRoles;
        this.returnType = returnType;
        this.parameters = parameters;
    }

    /**
     * @return The active query builder
     */
    public QueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return methodElement.getAnnotationMetadata();
    }

    /**
     * Check whether the given type performs the given role.
     * @param type The type
     * @param role The role
     * @return True if it is
     */
    public boolean isTypeInRole(@NonNull ClassElement type, @NonNull String role) {
        //noinspection ConstantConditions
        if (type != null && role != null) {
            String r = this.typeRoles.get(type.getName());
            return r != null && r.equalsIgnoreCase(role);
        }
        return false;
    }

    /**
     * @return The visitor context
     */
    @NonNull
    public VisitorContext getVisitorContext() {
        return visitorContext;
    }

    /**
     * @return The method element
     */
    @NonNull
    public MethodElement getMethodElement() {
        return methodElement;
    }

    /**
     * @return The return type
     */
    @NonNull
    public ClassElement getReturnType() {
        return returnType;
    }

    /**
     * @return The parameters
     */
    @NonNull
    public ParameterElement[] getParameters() {
        return parameters;
    }

    /**
     * Fail compilation with the given message for the current method.
     * @param message The message
     */
    public void fail(@NonNull String message) {
        this.failing = true;
        getVisitorContext().fail(getUnableToImplementMessage() + message, getMethodElement());
    }

    /**
     * Is there a current error.
     *
     * @return True if there is an error
     */
    public boolean isFailing() {
        return failing;
    }

    /**
     * Whether or not implicit queries such as lookup by id and counting is supported without an explicit query.
     * @return True if it is
     */
    public boolean supportsImplicitQueries() {
        return repositoryClass.booleanValue(RepositoryConfiguration.class, "implicitQueries").orElse(true);
    }

    /**
     * @return The repository class.
     */
    public @NonNull ClassElement getRepositoryClass() {
        return repositoryClass;
    }

    /**
     * @return The message to print in the case of no possible implementations.
     */
    public String getUnableToImplementMessage() {
        return "Unable to implement Repository method: " + repositoryClass.getSimpleName() + "." + methodElement.getName() + "(" + Arrays.stream(methodElement.getParameters()).map(p -> p.getType().getSimpleName() + " " + p.getName()).collect(Collectors.joining(",")) + "). ";
    }
}
