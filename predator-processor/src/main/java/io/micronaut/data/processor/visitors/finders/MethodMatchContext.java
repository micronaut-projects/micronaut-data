package io.micronaut.data.processor.visitors.finders;

import io.micronaut.data.model.PersistentEntity;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A match context for finding a matching method.
 *
 * @author graemerocher
 * @since 1.0
 */
public class MethodMatchContext {

    @Nonnull
    private final PersistentEntity entity;
    @Nonnull
    private final VisitorContext visitorContext;
    @Nonnull
    final MethodElement methodElement;
    @Nullable
    final ParameterElement paginationParameter;
    @Nonnull
    final ParameterElement[] parameters;

    public MethodMatchContext(
            @Nonnull PersistentEntity entity,
            @Nonnull VisitorContext visitorContext,
            @Nonnull MethodElement methodElement,
            @Nullable ParameterElement paginationParameter,
            @Nonnull ParameterElement[] parameters) {
        this.entity = entity;
        this.visitorContext = visitorContext;
        this.methodElement = methodElement;
        this.paginationParameter = paginationParameter;
        this.parameters = parameters;
    }

    @Nonnull
    public PersistentEntity getEntity() {
        return entity;
    }

    @Nonnull
    public VisitorContext getVisitorContext() {
        return visitorContext;
    }

    @Nonnull
    public MethodElement getMethodElement() {
        return methodElement;
    }

    @Nullable
    public ParameterElement getPaginationParameter() {
        return paginationParameter;
    }

    @Nonnull
    public ParameterElement[] getParameters() {
        return parameters;
    }
}
