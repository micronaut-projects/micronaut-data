package io.micronaut.data.processor.visitors.finders;

import io.micronaut.data.intercept.SaveAllInterceptor;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * A save all method for saving several entities.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SaveAllMethod extends AbstractPatternBasedMethod {

    private static final String METHOD_PATTERN = "^((save|persist|store|insert)(\\S*?))$";

    /**
     * Default constructor.
     */
    public SaveAllMethod() {
        super(Pattern.compile(METHOD_PATTERN));
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        ParameterElement[] parameters = methodElement.getParameters();
        if(parameters.length == 1) {
            ParameterElement firstParameter = parameters[0];
            return super.isMethodMatch(methodElement) && TypeUtils.isIterableOfEntity(firstParameter.getGenericType());
        }
        return false;
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@Nonnull MethodMatchContext matchContext) {
        // default doesn't build a query and query construction left to runtime
        // this is fine for JPA, for SQL we need to build an insert

        return new MethodMatchInfo(
                null,
                null,
                SaveAllInterceptor.class
        );
    }
}
