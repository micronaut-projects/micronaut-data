package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.intercept.FindAllByInterceptor;
import io.micronaut.data.intercept.FindAllInterceptor;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.query.Sort;
import io.micronaut.inject.ast.ParameterElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AbstractListMethod extends AbstractPatternBasedMethod {

    protected AbstractListMethod(String...prefixes) {
        super(computePattern(prefixes));
    }

    private static Pattern computePattern(String[] prefixes) {
        String prefixPattern = String.join("|", prefixes);
        return Pattern.compile("^((" + prefixPattern + ")(\\S*?))$");
    }

    @Nullable
    @Override
    public PredatorMethodInfo buildMatchInfo(@Nonnull MethodMatchContext matchContext) {
        ParameterElement[] parameters = matchContext.getParameters();
        ParameterElement paginationParameter = matchContext.getPaginationParameter();
        List<ParameterElement> queryParams = Arrays.stream(parameters).filter(p -> p != paginationParameter).collect(Collectors.toList());
        Query query = null;
        if (CollectionUtils.isNotEmpty(queryParams)) {
            PersistentEntity entity = matchContext.getEntity();
            query = Query.from(entity);
            for (ParameterElement queryParam : queryParams) {
                String paramName = queryParam.getName();
                PersistentProperty prop = entity.getPropertyByName(paramName);
                if (prop == null) {
                    matchContext.getVisitorContext().fail(
                            "Cannot query entity [" + entity.getSimpleName() + "] on non-existent property: " + paramName,
                            queryParam
                    );
                    return null;
                } else {
                    query.eq(prop.getName(), new QueryParameter(queryParam.getName()));
                }
            }

        }
        String methodName = matchContext.getMethodElement().getName();
        Matcher matcher = pattern.matcher(methodName);
        if (matcher.find()) {
            String querySequence = matcher.group(3);
            ArrayList<Sort.Order> orderBys = new ArrayList<>(2);
            querySequence = matchOrder(querySequence, orderBys);
            if (CollectionUtils.isNotEmpty(orderBys)) {
                if (query == null) {
                    query = Query.from(matchContext.getEntity());
                    for (Sort.Order orderBy : orderBys) {
                        query.order(orderBy);
                    }
                }
            }
        }

        if (query != null) {
            return buildInfo(matchContext, query);
        } else {
            return buildInfo(matchContext, null);
        }
    }

    /**
     * Builds the info.
     *
     * @param matchContext The match context
     * @param query The query
     * @return The info
     */
    protected @Nonnull PredatorMethodInfo buildInfo(
            @NonNull MethodMatchContext matchContext, @Nullable Query query) {
        if (query != null) {
            return new PredatorMethodInfo(
                    matchContext.getEntity(),
                    query,
                    FindAllByInterceptor.class
            );
        } else {
            return new PredatorMethodInfo(
                    matchContext.getEntity(),
                    null,
                    FindAllInterceptor.class
            );
        }
    }

}
