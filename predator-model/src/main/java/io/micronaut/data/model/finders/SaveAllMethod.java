package io.micronaut.data.model.finders;

import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.intercept.SaveAllInterceptor;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Pattern;

public class SaveAllMethod extends AbstractPatternBasedMethod {
    private static final String METHOD_PATTERN = "^((save|persist|store|insert)(\\S*?))$";

    public SaveAllMethod() {
        super(Pattern.compile(METHOD_PATTERN));
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        ParameterElement[] parameters = methodElement.getParameters();
        if(parameters.length == 1) {
            ParameterElement firstParameter = parameters[0];
            return super.isMethodMatch(methodElement) && isIterableOfEntity(firstParameter.getGenericType());
        }
        return false;
    }

    @Nullable
    @Override
    public Query buildQuery(
            @Nonnull PersistentEntity entity,
            @Nonnull MethodElement methodElement,
            @Nonnull VisitorContext visitorContext) {
        // default doesn't build a query and query construction left to runtime
        // this is fine for JPA
        return null;
    }

    @Nullable
    @Override
    public Class<? extends PredatorInterceptor> getRuntimeInterceptor(
            @Nonnull PersistentEntity entity,
            @Nonnull MethodElement methodElement,
            @Nonnull VisitorContext visitorContext) {
        return SaveAllInterceptor.class;
    }
}
