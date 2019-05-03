package io.micronaut.data.processor.visitors.finders;

import io.micronaut.data.annotation.Persisted;
import io.micronaut.data.intercept.*;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;
import org.reactivestreams.Publisher;

import javax.annotation.Nonnull;
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
    protected PredatorMethodInfo buildMethodInfo(@Nullable Query query, @Nonnull PersistentEntity entity, @Nonnull VisitorContext visitorContext, @Nonnull MethodElement methodElement, @Nullable ParameterElement paginationParameter, @Nonnull ParameterElement[] parameters) {
        ClassElement returnType = methodElement.getGenericReturnType();
        if (returnType != null) {
            if (returnType.hasAnnotation(Persisted.class)) {
                return new PredatorMethodInfo(query, FindOneInterceptor.class);
            } else if (returnType.isAssignable(Iterable.class) && hasPersistedTypeArgument(returnType)) {
                return new PredatorMethodInfo(query, FindAllByInterceptor.class);
            } else if(returnType.isAssignable(Stream.class) && hasPersistedTypeArgument(returnType)) {
                return new PredatorMethodInfo(query, FindStreamInterceptor.class);
            } else if(returnType.isAssignable(Optional.class) && hasPersistedTypeArgument(returnType)) {
                return new PredatorMethodInfo(query, FindOptionalInterceptor.class);
            } else if(returnType.isAssignable(Publisher.class) && hasPersistedTypeArgument(returnType)) {
                return new PredatorMethodInfo(query, FindReactivePublisherInterceptor.class);
            } else {
                // TODO: handle projections, reactive single etc.
            }
        }

        visitorContext.fail("Unsupported Repository method return type", methodElement);
        return null;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return super.isMethodMatch(methodElement) && isCompatibleReturnType(methodElement);
    }

    private boolean isCompatibleReturnType(MethodElement methodElement) {
        ClassElement returnType = methodElement.getGenericReturnType();
        if (returnType != null) {
            return returnType.hasAnnotation(Persisted.class) ||
                    isContainerType(returnType);
        }
        return false;
    }

}
