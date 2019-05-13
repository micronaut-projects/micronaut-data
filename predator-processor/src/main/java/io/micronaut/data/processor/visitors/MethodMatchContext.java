package io.micronaut.data.processor.visitors;

import edu.umd.cs.findbugs.annotations.NonNull;
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
    private boolean failing = false;

    /**
     * Creates the context.
     * @param entity The entity
     * @param visitorContext The visitor context
     * @param returnType The return type
     * @param methodElement The method element
     * @param parametersInRole Parameters that fulfill a query execution role
     * @param typeRoles The type roles
     * @param parameters The parameters
     */
    MethodMatchContext(
            @NonNull SourcePersistentEntity entity,
            @NonNull VisitorContext visitorContext,
            @NonNull ClassElement returnType,
            @NonNull MethodElement methodElement,
            @NonNull Map<String, Element> parametersInRole,
            @NonNull Map<String, String> typeRoles,
            @NonNull ParameterElement[] parameters) {
        super(visitorContext, methodElement, typeRoles, returnType, parameters);
        this.entity = entity;
        this.parametersInRole = Collections.unmodifiableMap(parametersInRole);
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
     * Fail compilation with the given message for the current method.
     * @param message The message
     */
    public void fail(@NonNull String message) {
        this.failing = true;
        getVisitorContext().fail(message, getMethodElement());
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
     * Returns a list of parameters that are not fulfilling a specific query role.
     * @return The parameters not in role
     */
    public @NonNull List<ParameterElement> getParametersNotInRole() {
        return Arrays.stream(getParameters()).filter(p ->
            !this.parametersInRole.containsValue(p)
        ).collect(Collectors.toList());
    }
}
