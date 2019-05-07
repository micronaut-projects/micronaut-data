package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.intercept.*;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import org.reactivestreams.Publisher;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finder used to return a single result
 */
public class FindByFinder extends DynamicFinder {

    protected static final String[] PREFIXES = {"find", "get", "query", "retrieve", "read", "search"};

    public FindByFinder() {
        super(PREFIXES);
    }

    @Nullable
    @Override
    protected PredatorMethodInfo buildInfo(MethodMatchContext matchContext, @NonNull ClassElement queryResultType, @Nullable Query query) {
        ClassElement returnType = matchContext.getReturnType();
        ClassElement typeArgument = returnType.getFirstTypeArgument().orElse(null);
        if (!returnType.getName().equals("void")) {
            if (returnType.hasStereotype(Introspected.class) || ClassUtils.isJavaBasicType(returnType.getName()) || returnType.isPrimitive()) {
                if (areTypesCompatible(returnType, queryResultType)) {
                    return new PredatorMethodInfo(queryResultType, query, FindOneInterceptor.class);
                } else {
                    matchContext.fail("Query results in a type [" + queryResultType.getName() + "] whilst method returns an incompatible type: " + returnType.getName());
                }
            } else if (typeArgument != null) {
                if (!areTypesCompatible(typeArgument, queryResultType)) {
                    matchContext.fail("Query results in a type [" + queryResultType.getName() + "] whilst method returns an incompatible type: " + returnType.getName());
                    return null;
                }

                if (returnType.isAssignable(Iterable.class)) {
                    return new PredatorMethodInfo(typeArgument, query, FindAllByInterceptor.class);
                } else if (returnType.isAssignable(Stream.class)) {
                    return new PredatorMethodInfo(typeArgument, query, FindStreamInterceptor.class);
                } else if (returnType.isAssignable(Optional.class)) {
                    return new PredatorMethodInfo(typeArgument, query, FindOptionalInterceptor.class);
                } else if (returnType.isAssignable(Publisher.class)) {
                    return new PredatorMethodInfo(typeArgument, query, FindReactivePublisherInterceptor.class);
                }
            }
        }

        matchContext.fail("Unsupported Repository method return type");
        return null;
    }

    private boolean areTypesCompatible(ClassElement returnType, ClassElement queryResultType) {
        if (returnType.isAssignable(queryResultType.getName())) {
            return true;
        } else {
            if (TypeUtils.isNumber(returnType) && TypeUtils.isNumber(queryResultType)) {
                return true;
            } else if (TypeUtils.isBoolean(returnType) && TypeUtils.isBoolean(queryResultType)) {
                return true;
            }
        }
        return false;
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
