package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * A match context for finding a matching method.
 *
 * @author graemerocher
 * @since 1.0
 */
public class MethodMatchContext {

    @NonNull
    private final SourcePersistentEntity entity;
    @NonNull
    private final VisitorContext visitorContext;
    @NonNull
    private final MethodElement methodElement;
    @Nullable
    private final ParameterElement paginationParameter;
    @NonNull
    private final ParameterElement[] parameters;
    private final ClassElement returnType;

    public MethodMatchContext(
            @NonNull SourcePersistentEntity entity,
            @NonNull VisitorContext visitorContext,
            @NonNull ClassElement returnType,
            @NonNull MethodElement methodElement,
            @Nullable ParameterElement paginationParameter,
            @NonNull ParameterElement[] parameters) {
        this.entity = entity;
        this.visitorContext = visitorContext;
        this.methodElement = methodElement;
        this.paginationParameter = paginationParameter;
        this.parameters = parameters;
        this.returnType = returnType;
    }

    @NonNull
    public SourcePersistentEntity getEntity() {
        return entity;
    }

    @NonNull
    public VisitorContext getVisitorContext() {
        return visitorContext;
    }

    @NonNull
    public MethodElement getMethodElement() {
        return methodElement;
    }

    @NonNull
    public ClassElement getReturnType() {
        return returnType;
    }

    @Nullable
    public ParameterElement getPaginationParameter() {
        return paginationParameter;
    }

    @NonNull
    public ParameterElement[] getParameters() {
        return parameters;
    }

    /**
     * Fail compilation with the given message for the current method.
     * @param message The message
     */
    public void fail(@NonNull String message) {
        getVisitorContext().fail(message, methodElement);
    }
}
