package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.intercept.FindOneInterceptor;
import io.micronaut.inject.ast.ClassElement;

import javax.annotation.Nonnull;

public class QueryOneMethod extends QueryListMethod {
    @Override
    protected boolean isValidReturnType(@Nonnull ClassElement returnType) {
        return returnType.hasStereotype(Introspected.class);
    }

    @Override
    protected PredatorMethodInfo buildMatchInfo(@Nonnull MethodMatchContext matchContext, @Nonnull RawQuery query) {
        return new PredatorMethodInfo(
            query,
            FindOneInterceptor.class
        );
    }
}
