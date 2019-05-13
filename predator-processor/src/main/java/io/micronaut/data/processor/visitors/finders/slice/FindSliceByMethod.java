package io.micronaut.data.processor.visitors.finders.slice;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.FindSliceInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.FindByFinder;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

import javax.annotation.Nullable;

public class FindSliceByMethod extends FindByFinder {

    public static final int POSITION = DEFAULT_POSITION - 150;

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Nullable
    @Override
    protected MethodMatchInfo buildInfo(MethodMatchContext matchContext, @NonNull ClassElement queryResultType, @Nullable Query query) {
        if (!matchContext.hasParameterInRole(TypeRole.PAGEABLE)) {
            matchContext.fail("Method must accept an argument that is a Pageable");
            return null;
        }
        return new MethodMatchInfo(
                queryResultType,
                query,
                FindSliceInterceptor.class
        );
    }

    @Override
    protected boolean isCompatibleReturnType(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {
        return matchContext.isTypeInRole(
                matchContext.getReturnType(),
                TypeRole.SLICE
        );
    }
}
