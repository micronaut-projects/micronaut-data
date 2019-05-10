package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.annotation.Persisted;
import io.micronaut.data.intercept.SaveEntityInterceptor;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * A save method for saving a single entity.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SaveMethod extends AbstractPatternBasedMethod implements MethodCandidate {

    private static final String METHOD_PATTERN = "^((save|persist|store|insert)(\\S*?))$";

    /**
     * The default constructor.
     */
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
    public MethodMatchInfo buildMatchInfo(@Nonnull MethodMatchContext matchContext) {
        VisitorContext visitorContext = matchContext.getVisitorContext();
        ParameterElement[] parameters = matchContext.getParameters();
        if (ArrayUtils.isNotEmpty(parameters)) {
            if (Arrays.stream(parameters).anyMatch(p -> {
                ClassElement t = p.getGenericType();
                return t != null && t.hasAnnotation(Persisted.class);
            })) {
                return new MethodMatchInfo(matchContext.getReturnType(), null, SaveEntityInterceptor.class);
            }
        }
        visitorContext.fail("Cannot implement save method for specified arguments and return type", matchContext.getMethodElement());
        return null;
    }

}
