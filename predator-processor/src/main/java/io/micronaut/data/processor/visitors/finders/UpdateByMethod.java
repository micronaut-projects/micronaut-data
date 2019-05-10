package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.intercept.UpdateInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Support for finder based updates.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class UpdateByMethod extends DynamicFinder {

    /**
     * Default constructor.
     */
    public UpdateByMethod() {
        super("update");
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return super.isMethodMatch(methodElement) && TypeUtils.doesReturnVoid(methodElement);
    }

    @Nullable
    @Override
    protected MethodMatchInfo buildInfo(
            MethodMatchContext matchContext,
            @NonNull ClassElement queryResultType,
            @Nullable Query query) {
        if (query == null) {
            matchContext.fail("Cannot implement batch update operation that doesn't perform a query");
            return null;
        }
        if (CollectionUtils.isNotEmpty(query.getProjections())) {
            matchContext.fail("Projections are not supported on batch updates");
            return null;
        }
        List<Query.Criterion> criterionList = query.getCriteria().getCriteria();
        if (CollectionUtils.isEmpty(criterionList)) {
            matchContext.fail("Cannot implement batch update operation that doesn't perform a query");
            return null;
        }
        Set<String> queryParameters = new HashSet<>();
        for (Query.Criterion criterion : criterionList) {
            if (criterion instanceof Query.PropertyCriterion) {
                Query.PropertyCriterion pc = (Query.PropertyCriterion) criterion;
                Object v = pc.getValue();
                if (v instanceof QueryParameter) {
                    queryParameters.add(((QueryParameter) v).getName());
                }
            }
        }
        List<ParameterElement> updateParameters = Arrays.stream(matchContext.getParameters()).filter(p -> !queryParameters.contains(p.getName()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(updateParameters)) {
            matchContext.fail("At least one parameter required to update");
            return null;
        }
        SourcePersistentEntity entity = matchContext.getRootEntity();
        String[] updateProperties = new String[updateParameters.size()];
        for (int i = 0; i < updateProperties.length; i++) {
            ParameterElement parameter = updateParameters.get(i);
            String parameterName = parameter.getName();
            Optional<String> path = entity.getPath(parameterName);
            if (path.isPresent()) {
                updateProperties[i] = path.get();
            } else {
                matchContext.fail("Cannot perform batch update for non-existent property: " + parameterName);
                return null;
            }
        }
        return new MethodMatchInfo(
                queryResultType,
                query,
                UpdateInterceptor.class,
                MethodMatchInfo.OperationType.UPDATE,
                updateProperties
        );
    }
}
