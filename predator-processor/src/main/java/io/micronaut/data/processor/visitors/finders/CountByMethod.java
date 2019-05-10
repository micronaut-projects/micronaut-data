package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.intercept.CountInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

import javax.annotation.Nullable;

/**
 * Dynamic finder for support for counting.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class CountByMethod extends DynamicFinder {

    /**
     * Default constructor.
     */
    public CountByMethod() {
        super("count");
    }

    @Nullable
    @Override
    protected MethodMatchInfo buildInfo(@NonNull MethodMatchContext matchContext, ClassElement queryResultType, @Nullable Query query) {
        if (query != null) {
            query.projections().count();
            return new MethodMatchInfo(
                    matchContext.getReturnType(),
                    query,
                    CountInterceptor.class
            );
        } else {
            return new MethodMatchInfo(
                    matchContext.getReturnType(),
                    null,
                    CountInterceptor.class
            );
        }
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return super.isMethodMatch(methodElement) && TypeUtils.doesReturnNumber(methodElement);
    }
}
