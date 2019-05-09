package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.intercept.CountInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

import javax.annotation.Nullable;

public class CountByMethod extends DynamicFinder {

    public CountByMethod() {
        super("count");
    }

    @Nullable
    @Override
    protected PredatorMethodInfo buildInfo(@NonNull MethodMatchContext matchContext, ClassElement queryResultType, @Nullable Query query) {
        if (query != null) {
            query.projections().count();
            return new PredatorMethodInfo(
                    matchContext.getReturnType(),
                    query,
                    CountInterceptor.class
            );
        } else {
            return new PredatorMethodInfo(
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
