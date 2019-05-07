package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.intercept.ExistsByInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

public class ExistsByFinder extends DynamicFinder {

    public ExistsByFinder() {
        super("exists");
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return super.isMethodMatch(methodElement) && TypeUtils.doesReturnBoolean(methodElement);
    }

    @Nullable
    @Override
    protected PredatorMethodInfo buildInfo(
            @NonNull MethodMatchContext matchContext,
            ClassElement queryResultType, @Nullable Query query) {
        if (query != null) {
            query.projections().id();
        }
        return new PredatorMethodInfo(
                matchContext.getReturnType(),
                query,
                ExistsByInterceptor.class
        );
    }

}
