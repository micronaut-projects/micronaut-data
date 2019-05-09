package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.query.Sort;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
        SourcePersistentEntity rootEntity = matchContext.getEntity();
        if (CollectionUtils.isNotEmpty(queryParams)) {
            query = Query.from(rootEntity);
            for (ParameterElement queryParam : queryParams) {
                String paramName = queryParam.getName();
                PersistentProperty prop = ((PersistentEntity) rootEntity).getPropertyByName(paramName);
                if (prop == null) {
                    matchContext.getVisitorContext().fail(
                            "Cannot query entity [" + ((PersistentEntity) rootEntity).getSimpleName() + "] on non-existent property: " + paramName,
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
        ClassElement queryResultType = rootEntity.getClassElement();

        if (matcher.find()) {
            String querySequence = matcher.group(3);
            ArrayList<Sort.Order> orderBys = new ArrayList<>(2);
            querySequence = matchOrder(querySequence, orderBys);
            if (CollectionUtils.isNotEmpty(orderBys)) {
                if (query == null) {
                    query = Query.from(rootEntity);
                }
                for (Sort.Order orderBy : orderBys) {
                    String property = orderBy.getProperty();
                    Optional<String> propertyPath = rootEntity.getPath(property);
                    if (propertyPath.isPresent()) {
                        query.order(orderBy);
                    } else {
                        matchContext.fail("Cannot order by non-existent property: " + property);
                        return null;
                    }
                }
            }

            if (StringUtils.isNotEmpty(querySequence)) {
                ArrayList<ProjectionMethodExpression> projections = new ArrayList<>();
                matchProjections(matchContext, projections, querySequence);
                if (CollectionUtils.isNotEmpty(projections)) {
                    if (query == null) {
                        query = Query.from(rootEntity);
                    }
                    for (ProjectionMethodExpression projection : projections) {
                        projection.apply(matchContext, query);
                        queryResultType = projection.getExpectedResultType();
                    }

                    if (projections.size() > 1) {
                        queryResultType = matchContext.getVisitorContext().getClassElement(Object.class).orElse(rootEntity.getClassElement());
                    }

                    for (Query.Projection projection : query.getProjections()) {
                        if (projection instanceof Query.PropertyProjection) {
                            Query.PropertyProjection pp = (Query.PropertyProjection) projection;
                            String prop = pp.getPropertyName();
                            Optional<String> path = rootEntity.getPath(prop);
                            if (!path.isPresent()) {
                                matchContext.fail("Cannot project on non-existent property: " + prop);
                                return null;
                            }
                        }
                    }
                }
            }
        }


        if (query != null) {
            return buildInfo(matchContext, queryResultType, query);
        } else {
            return buildInfo(matchContext, queryResultType, null);
        }
    }

}
