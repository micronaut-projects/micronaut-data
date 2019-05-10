package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.intercept.CountInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

import javax.annotation.Nullable;

/**
 * Simple count method.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class CountMethod extends AbstractListMethod {

    public CountMethod() {
        super("count");
    }

    @Override
    public int getOrder() {
        // lower priority than dynamic finder
        return DEFAULT_POSITION + 100;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return super.isMethodMatch(methodElement) && TypeUtils.doesReturnNumber(methodElement);
    }

    @Nullable
    @Override
    protected MethodMatchInfo buildInfo(@NonNull MethodMatchContext matchContext, @NonNull ClassElement queryResultType, @Nullable Query query) {
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

}
