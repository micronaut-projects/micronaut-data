package io.micronaut.data.processor.visitors;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Map;

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

    /**
     * Default constructor.
     * @param visitorContext The visitor context
     * @param methodElement The method element
     * @param typeRoles The type roles
     * @param returnType The return type
     * @param parameters The parameters
     */
    MatchContext(
            @NonNull VisitorContext visitorContext,
            @NonNull MethodElement methodElement,
            @NonNull Map<String, String> typeRoles,
            @NonNull ClassElement returnType,
            @NonNull ParameterElement[] parameters) {
        this.visitorContext = visitorContext;
        this.methodElement = methodElement;
        this.typeRoles = typeRoles;
        this.returnType = returnType;
        this.parameters = parameters;
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
}
