package io.micronaut.data.processor.visitors.finders;

import io.micronaut.data.intercept.DeleteByInterceptor;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Pattern;

public class DeleteByMethod extends AbstractFindByFinder {

    private static final String METHOD_PATTERN = "((delete|remove|erase)(\\S*?)By)([A-Z]\\w*)";

    public DeleteByMethod() {
        super(Pattern.compile(METHOD_PATTERN));
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return super.isMethodMatch(methodElement) && doesReturnVoid(methodElement); // void return
    }

    @Nullable
    @Override
    protected PredatorMethodInfo buildMethodInfo(
            @Nullable Query query,
            @Nonnull PersistentEntity entity,
            @Nonnull VisitorContext visitorContext,
            @Nonnull MethodElement methodElement,
            @Nullable ParameterElement paginationParameter,
            @Nonnull ParameterElement[] parameters) {
        if (query == null) {
            visitorContext.fail("Unable to implement delete method with no query arguments", methodElement);
            return null;
        } else {
            return new PredatorMethodInfo(
                    query,
                    DeleteByInterceptor.class,
                    PredatorMethodInfo.OperationType.DELETE
            );
        }
    }
}
