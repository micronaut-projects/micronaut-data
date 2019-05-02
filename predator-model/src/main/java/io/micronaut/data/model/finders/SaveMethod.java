package io.micronaut.data.model.finders;

import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.annotation.Persisted;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.intercept.SaveEntityInterceptor;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.regex.Pattern;

public class SaveMethod extends AbstractPatternBasedMethod implements FinderMethod {

    private static final String METHOD_PATTERN = "^((save|persist|store|insert)(\\S*?))$";

    public SaveMethod() {
        super(Pattern.compile(METHOD_PATTERN));
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        ClassElement returnType = methodElement.getGenericReturnType();
        boolean returnTypeValid = returnType == null || returnType.hasAnnotation(Persisted.class);
        return returnTypeValid && super.isMethodMatch(methodElement);
    }

    @Nullable
    @Override
    public Query buildQuery(
            @Nonnull PersistentEntity entity,
            @Nonnull MethodElement methodElement,
            @Nonnull VisitorContext visitorContext) {
        // no query involved
        return null;
    }

    @Nullable
    @Override
    public Class<? extends PredatorInterceptor> getRuntimeInterceptor(
            @Nonnull PersistentEntity entity,
            @Nonnull MethodElement methodElement,
            @Nonnull VisitorContext visitorContext) {
        ParameterElement[] parameters = methodElement.getParameters();
        if (ArrayUtils.isNotEmpty(parameters)) {
            if (Arrays.stream(parameters).anyMatch(p -> {
                ClassElement t = p.getGenericType();
                return t != null && t.hasAnnotation(Persisted.class);
            })) {
                return SaveEntityInterceptor.class;
            }
        }
        visitorContext.fail("Cannot implement save method for specified arguments and return type", methodElement);
        return null;
    }
}
