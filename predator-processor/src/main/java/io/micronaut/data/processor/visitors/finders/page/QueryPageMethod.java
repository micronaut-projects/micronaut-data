package io.micronaut.data.processor.visitors.finders.page;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.FindPageInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.QueryListMethod;
import io.micronaut.inject.ast.ClassElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class QueryPageMethod extends QueryListMethod {
    @Override
    protected boolean isValidReturnType(@Nonnull ClassElement returnType, MatchContext matchContext) {
        return matchContext.isTypeInRole(returnType, TypeRole.PAGE);
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
}
