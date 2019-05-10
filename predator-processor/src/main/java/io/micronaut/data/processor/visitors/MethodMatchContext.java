package io.micronaut.data.processor.visitors;

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
    private boolean failing = false;

    /**
     * Creates the context.
     * @param entity The entity
     * @param visitorContext The visitor context
     * @param returnType The return type
     * @param methodElement The method element
     * @param paginationParameter The pagination parameter
     * @param parameters The parameters
     */
    MethodMatchContext(
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

    /**
     * The root entity being queried.
     * @return The root entity
     */
    @NonNull
    public SourcePersistentEntity getRootEntity() {
        return entity;
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
     * @return The pagination parameter
     */
    @Nullable
    public ParameterElement getPaginationParameter() {
        return paginationParameter;
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
        getVisitorContext().fail(message, methodElement);
    }

    /**
     * Is there a current error.
     *
     * @return True if there is an error
     */
    public boolean isFailing() {
        return failing;
    }
}
