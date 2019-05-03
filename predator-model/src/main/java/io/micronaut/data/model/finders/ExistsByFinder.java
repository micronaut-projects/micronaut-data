package io.micronaut.data.model.finders;

import io.micronaut.data.intercept.ExistsByInterceptor;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Pattern;

public class ExistsByFinder extends AbstractFindByFinder {

    private static final String METHOD_PATTERN = "((exists)(\\S*?)By)([A-Z]\\w*)";

    public ExistsByFinder() {
        super(Pattern.compile(METHOD_PATTERN));
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return super.isMethodMatch(methodElement) && isCompatibleReturnType(methodElement);
    }

    @Override
    public Query buildQuery(@Nonnull PersistentEntity entity, @Nonnull MethodElement methodElement, VisitorContext visitorContext) {
        Query query = super.buildQuery(entity, methodElement, visitorContext);
        if (query != null) {
            query.projections().id();
        }
        return query;
    }

    @Nullable
    @Override
    public Class<? extends PredatorInterceptor> getRuntimeInterceptor(@Nonnull PersistentEntity entity, @Nonnull MethodElement methodElement, @Nonnull VisitorContext visitorContext) {
        return ExistsByInterceptor.class;
    }

    private boolean isCompatibleReturnType(MethodElement methodElement) {
        ClassElement returnType = methodElement.getGenericReturnType();
        if (returnType != null) {
            return returnType.isAssignable(Boolean.class) || (returnType.isPrimitive() && returnType.getName().equals("boolean"));
        }
        return false;
    }
}
