package io.micronaut.data.processor.visitors.finders.page;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.FindPageInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.FindByFinder;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.slice.FindSliceByMethod;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

import javax.annotation.Nullable;

public class FindPageByMethod extends FindByFinder {

    protected static final int POSITION = FindSliceByMethod.POSITION + 10;

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
                FindPageInterceptor.class
        );
    }

    @Override
    protected boolean isCompatibleReturnType(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {
        return matchContext.isTypeInRole(
                matchContext.getReturnType(),
                TypeRole.PAGE
        );
    }
}
