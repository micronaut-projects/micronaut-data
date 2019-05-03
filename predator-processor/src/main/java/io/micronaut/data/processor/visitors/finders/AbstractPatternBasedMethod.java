package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.data.annotation.Persisted;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import org.reactivestreams.Publisher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

abstract class AbstractPatternBasedMethod implements PredatorMethodCandidate {

    protected final Pattern pattern;

    public AbstractPatternBasedMethod(@Nonnull Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return pattern.matcher(methodElement.getName()).find();
    }

    protected boolean isIterableOfEntity(@Nullable ClassElement type) {
        return type != null && type.isAssignable(Iterable.class) && hasPersistedTypeArgument(type);
    }

    protected boolean isContainerType(ClassElement returnType) {
        return (returnType.isAssignable(Iterable.class) ||
                returnType.isAssignable(Stream.class) ||
                returnType.isAssignable(Publisher.class) ||
                returnType.isAssignable(Optional.class)) && hasPersistedTypeArgument(returnType);
    }

    protected boolean hasPersistedTypeArgument(ClassElement returnType) {
        return returnType.getFirstTypeArgument().map(t -> t.hasAnnotation(Persisted.class)).orElse(false);
    }

    /**
     * Does the method return an object convertible to a number.
     * @param methodElement The method element
     * @return True if it does
     */
    protected boolean doesReturnNumber(@Nonnull MethodElement methodElement) {
        ClassElement returnType = methodElement.getGenericReturnType();
        if (returnType != null) {
            if (returnType.isPrimitive()) {
                return ClassUtils.getPrimitiveType(returnType.getName()).map(aClass ->
                        Number.class.isAssignableFrom(ReflectionUtils.getWrapperType(aClass))
                ).orElse(false);
            } else {
                return returnType.isAssignable(Number.class);
            }
        } else {
            return false;
        }
    }
}
