package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.intercept.FindOneInterceptor;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;

import javax.annotation.Nonnull;

/**
 * Support for explicit queries that return a single result.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class QueryOneMethod extends QueryListMethod {
    @Override
    protected boolean isValidReturnType(@Nonnull ClassElement returnType) {
        return returnType.hasStereotype(Introspected.class);
    }

    @Override
    protected MethodMatchInfo buildMatchInfo(@Nonnull MethodMatchContext matchContext, @Nonnull RawQuery query) {
        return new MethodMatchInfo(
            matchContext.getReturnType(),
            query,
            FindOneInterceptor.class
        );
    }
}
