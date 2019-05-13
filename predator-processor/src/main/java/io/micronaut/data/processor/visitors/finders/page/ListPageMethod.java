package io.micronaut.data.processor.visitors.finders.page;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.FindPageInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.ListMethod;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.inject.ast.ClassElement;


public class ListPageMethod extends ListMethod {
    protected static final int POSITION = FindPageByMethod.POSITION + 25;

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
    protected boolean isValidReturnType(@NonNull ClassElement returnType, MatchContext matchContext) {
        return matchContext.isTypeInRole(
                matchContext.getReturnType(),
                TypeRole.PAGE
        );
    }

}
