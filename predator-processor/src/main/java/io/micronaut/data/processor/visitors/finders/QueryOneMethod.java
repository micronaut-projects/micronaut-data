package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.intercept.FindOneInterceptor;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Support for explicit queries that return a single result.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class QueryOneMethod extends QueryListMethod {
    @Override
    protected boolean isValidReturnType(@NonNull ClassElement returnType, MatchContext matchContext) {
        return returnType.hasStereotype(Introspected.class);
    }

    @Override
    protected MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext, @NonNull RawQuery query) {
        return new MethodMatchInfo(
            matchContext.getReturnType(),
            query,
            FindOneInterceptor.class
        );
    }
}
