package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

/**
 * Finder used to return a single result.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class FindByFinder extends DynamicFinder {

    /**
     * The prefixes used.
     */
    public static final String[] PREFIXES = {"find", "get", "query", "retrieve", "read", "search"};

    /**
     * Default constructor.
     */
    public FindByFinder() {
        super(PREFIXES);
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return super.isMethodMatch(methodElement) && isCompatibleReturnType(methodElement);
    }

    private boolean isCompatibleReturnType(MethodElement methodElement) {
        ClassElement returnType = methodElement.getGenericReturnType();
        if (returnType != null && !returnType.getName().equals("void")) {
            return returnType.hasStereotype(Introspected.class) ||
                    returnType.isPrimitive() ||
                    ClassUtils.isJavaBasicType(returnType.getName()) ||
                    TypeUtils.isContainerType(returnType);
        }
        return false;
    }

}
