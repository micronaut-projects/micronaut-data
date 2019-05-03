package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.intercept.FindAllByInterceptor;
import io.micronaut.data.intercept.FindAllInterceptor;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.inject.ast.ParameterElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AbstractListMethod extends AbstractPatternBasedMethod {

    public AbstractListMethod(@Nonnull Pattern pattern) {
        super(pattern);
    }

    @Nullable
    @Override
    public final PredatorMethodInfo buildMatchInfo(@Nonnull MethodMatchContext matchContext) {
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
            return buildInfo(query);
        } else {
            return buildInfo(null);
        }
    }

    /**
     * Builds the info.
     * @param query The query
     * @return The info
     */
    protected @Nonnull PredatorMethodInfo buildInfo(
            @Nullable Query query) {
        if (query != null) {
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
