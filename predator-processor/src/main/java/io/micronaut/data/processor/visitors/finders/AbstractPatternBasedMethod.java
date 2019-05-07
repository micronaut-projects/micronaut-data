package io.micronaut.data.processor.visitors.finders;

import io.micronaut.inject.ast.MethodElement;

import javax.annotation.Nonnull;
import java.util.regex.Pattern;

abstract class AbstractPatternBasedMethod implements PredatorMethodCandidate {

    protected final Pattern pattern;

    public AbstractPatternBasedMethod(@Nonnull Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return pattern.matcher(methodElement.getName()).find();
    }

}
