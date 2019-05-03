package io.micronaut.data.processor.visitors.finders;

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
}
