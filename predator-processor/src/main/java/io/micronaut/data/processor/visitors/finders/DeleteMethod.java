package io.micronaut.data.processor.visitors.finders;

import io.micronaut.data.intercept.DeleteAllInterceptor;
import io.micronaut.data.intercept.DeleteByInterceptor;
import io.micronaut.data.intercept.DeleteOneInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DeleteMethod extends AbstractListMethod {

    public DeleteMethod() {
        super("delete", "remove", "erase", "eliminate");
    }

    @Override
    public int getOrder() {
        // lower priority than dynamic finder
        return DEFAULT_POSITION + 100;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return super.isMethodMatch(methodElement) && TypeUtils.doesReturnVoid(methodElement); // void return
    }

    @Nullable
    @Override
    public PredatorMethodInfo buildMatchInfo(@Nonnull MethodMatchContext matchContext) {
        ParameterElement[] parameters = matchContext.getParameters();
        if (parameters.length == 1) {
            ClassElement genericType = parameters[0].getGenericType();
            if (genericType != null) {
                if (genericType.isAssignable(matchContext.getEntity().getName())) {

                    return new PredatorMethodInfo(
                            null,
                            null,
                            DeleteOneInterceptor.class
                    );
                } else if(TypeUtils.isIterableOfEntity(genericType)) {
                    return new PredatorMethodInfo(
                            null,
                            null,
                            DeleteAllInterceptor.class
                    );
                }
            }
        }
        return super.buildMatchInfo(matchContext);
    }

    /**
     * Builds the info.
     *
     * @param matchContext
     * @param query The query
     * @return The info
     */
    @Override
    protected @Nonnull
    PredatorMethodInfo buildInfo(
            MethodMatchContext matchContext, @Nullable Query query) {
        if (query != null) {
            return new PredatorMethodInfo(
                    null,
                    query,
                    DeleteByInterceptor.class
            );
        } else {
            return new PredatorMethodInfo(
                    null,
                    null,
                    DeleteAllInterceptor.class
            );
        }
    }
}
