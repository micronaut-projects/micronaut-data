package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.intercept.FindAllByInterceptor;
import io.micronaut.data.intercept.FindAllInterceptor;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ListMethod extends AbstractPatternBasedMethod {

    private static final String METHOD_PATTERN = "^((list|find|search|query)(\\S*?))$";

    public ListMethod() {
        super(Pattern.compile(METHOD_PATTERN));
    }

    @Override
    public int getOrder() {
        // lower priority than dynamic finder
        return DEFAULT_POSITION + 100;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        ClassElement returnType = methodElement.getGenericReturnType();
        return super.isMethodMatch(methodElement) && returnType != null &&
                returnType.isAssignable(Iterable.class) &&
                hasPersistedTypeArgument(returnType);
    }

    @Nullable
    @Override
    public PredatorMethodInfo buildMatchInfo(@Nonnull MethodMatchContext matchContext) {
        ParameterElement[] parameters = matchContext.getParameters();
        ParameterElement paginationParameter = matchContext.getPaginationParameter();
        List<ParameterElement> queryParams = Arrays.stream(parameters).filter(p -> p != paginationParameter).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(queryParams)) {
            PersistentEntity entity = matchContext.getEntity();
            Query query = Query.from(entity);
            for (ParameterElement queryParam : queryParams) {
                String paramName = queryParam.getName();
                PersistentProperty prop = entity.getPropertyByName(paramName);
                if (prop == null) {
                    matchContext.getVisitorContext().fail(
                            "Cannot query entity on non-existent property: " + paramName,
                            queryParam
                    );
                    return null;
                } else {
                    query.eq(prop.getName(), new QueryParameter(queryParam.getName()));
                }
            }
            return new PredatorMethodInfo(
                    query,
                    FindAllByInterceptor.class
            );
        } else {
            return new PredatorMethodInfo(
                    null,
                    FindAllInterceptor.class
            );
        }
    }
}
