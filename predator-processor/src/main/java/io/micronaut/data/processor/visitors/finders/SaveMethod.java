package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.annotation.Persisted;
import io.micronaut.data.intercept.SaveEntityInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.regex.Pattern;

public class SaveMethod extends AbstractPatternBasedMethod implements PredatorMethodCandidate {

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
    public PredatorMethodInfo buildMatchInfo(@Nonnull MethodMatchContext matchContext) {
        VisitorContext visitorContext = matchContext.getVisitorContext();
        ParameterElement[] parameters = matchContext.getParameters();
        if (ArrayUtils.isNotEmpty(parameters)) {
            if (Arrays.stream(parameters).anyMatch(p -> {
                ClassElement t = p.getGenericType();
                return t != null && t.hasAnnotation(Persisted.class);
            })) {
                return new PredatorMethodInfo(matchContext.getReturnType(), null, SaveEntityInterceptor.class);
            }
        }
        visitorContext.fail("Cannot implement save method for specified arguments and return type", matchContext.getMethodElement());
        return null;
    }

    @Nullable
    @Override
    protected PredatorMethodInfo buildInfo(@NonNull MethodMatchContext matchContext, @NonNull ClassElement queryResultType, @Nullable Query query) {
        // no-op
        return null;
    }
}
