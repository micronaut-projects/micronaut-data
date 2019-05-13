package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.processor.visitors.MatchContext;
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
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        return super.isMethodMatch(methodElement, matchContext) && isCompatibleReturnType(methodElement, matchContext);
    }

    /**
     * Is the return type compatible.
     * @param methodElement The method element
     * @param matchContext The match context
     * @return The return type
     */
    protected boolean isCompatibleReturnType(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {
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
