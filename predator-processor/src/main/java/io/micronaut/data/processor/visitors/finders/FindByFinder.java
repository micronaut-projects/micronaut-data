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
public class FindByFinder extends AbstractFindByFinder {

    private static final String METHOD_PATTERN = "((find|get|query|retrieve|read)(\\S*?)By)([A-Z]\\w*)";

    public FindByFinder() {
        super(Pattern.compile(METHOD_PATTERN));
    }

    @Nullable
    @Override
    protected PredatorMethodInfo buildInfo(MethodMatchContext matchContext, @NonNull ClassElement queryResultType, @Nullable Query query) {
        ClassElement returnType = matchContext.getReturnType();
        ClassElement typeArgument = returnType.getFirstTypeArgument().orElse(null);
        if (!returnType.getName().equals("void")) {
            if (returnType.hasStereotype(Introspected.class) || ClassUtils.isJavaBasicType(returnType.getName()) || returnType.isPrimitive()) {
                if (areTypesCompatible(returnType, queryResultType)) {
                    return new PredatorMethodInfo(returnType, query, FindOneInterceptor.class);
                } else {
                    matchContext.fail("Query results in a type [" + queryResultType.getName()  + "] whilst method returns an incompatible type: " + returnType.getName());
                }
            } else if(typeArgument != null) {

                if (returnType.isAssignable(Iterable.class)) {
                    return new PredatorMethodInfo(typeArgument, query, FindAllByInterceptor.class);
                } else if(returnType.isAssignable(Stream.class)) {
                    return new PredatorMethodInfo(typeArgument, query, FindStreamInterceptor.class);
                } else if(returnType.isAssignable(Optional.class) && hasPersistedTypeArgument(returnType)) {
                    return new PredatorMethodInfo(typeArgument, query, FindOptionalInterceptor.class);
                } else if(returnType.isAssignable(Publisher.class) && hasPersistedTypeArgument(returnType)) {
                    return new PredatorMethodInfo(typeArgument, query, FindReactivePublisherInterceptor.class);
                } else {
                    // TODO: handle projections, reactive single etc.
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
            if (isNumber(returnType) && isNumber(queryResultType)) {
                return true;
            } else if(isBoolean(returnType) && isBoolean(queryResultType)) {
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
                    isEntityContainerType(returnType);
        }
        return false;
    }

}
