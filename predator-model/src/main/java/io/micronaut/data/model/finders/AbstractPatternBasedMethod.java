package io.micronaut.data.model.finders;

import io.micronaut.inject.ast.MethodElement;

import javax.annotation.Nonnull;
import java.util.regex.Pattern;

abstract class AbstractPatternBasedMethod implements FinderMethod {

    protected final Pattern pattern;

    public AbstractPatternBasedMethod(@Nonnull Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return pattern.matcher(methodElement.getName()).find();
    }
}
