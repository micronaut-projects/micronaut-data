/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.*;

import edu.umd.cs.findbugs.annotations.Nullable;
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

    @NonNull
    @Override
    protected MethodMatchInfo.OperationType getOperationType() {
        return MethodMatchInfo.OperationType.UPDATE;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        boolean isMatch = super.isMethodMatch(methodElement, matchContext);
        if (isMatch) {
            if (!TypeUtils.isValidBatchUpdateReturnType(methodElement)) {
                matchContext.possiblyFail("Update methods only support void or number based return types");
            } else {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean hasQueryAnnotation(@NonNull MethodElement methodElement) {
        final String str = methodElement.stringValue(Query.class).orElse(null);
        if (StringUtils.isNotEmpty(str)) {
            return str.trim().toLowerCase(Locale.ENGLISH).startsWith("update");
        }
        return false;
    }

    @Nullable
    @Override
    protected MethodMatchInfo buildInfo(
            MethodMatchContext matchContext,
            @NonNull ClassElement queryResultType,
            @Nullable QueryModel query) {
        if (query == null) {
            matchContext.fail("Cannot implement batch update operation that doesn't perform a query");
            return null;
        }
        if (CollectionUtils.isNotEmpty(query.getProjections())) {
            matchContext.fail("Projections are not supported on batch updates");
            return null;
        }
        if (!(query instanceof RawQuery)) {
            List<QueryModel.Criterion> criterionList = query.getCriteria().getCriteria();
            if (CollectionUtils.isEmpty(criterionList)) {
                matchContext.fail("Cannot implement batch update operation that doesn't perform a query");
                return null;
            }

            SourcePersistentEntity entity = matchContext.getRootEntity();

            Set<String> queryParameters = new HashSet<>();
            for (QueryModel.Criterion criterion : criterionList) {
                if (criterion instanceof QueryModel.PropertyCriterion) {
                    QueryModel.PropertyCriterion pc = (QueryModel.PropertyCriterion) criterion;
                    Object v = pc.getValue();
                    if (v instanceof QueryParameter) {
                        queryParameters.add(((QueryParameter) v).getName());
                    }
                }
            }

            List<String> updateProperties = matchContext.getParametersNotInRole()
                    .stream()
                    .map(p -> p.stringValue(Parameter.class).orElse(p.getName()))
                    .filter(parameterName -> !queryParameters.contains(parameterName))
                    .map(parameterName -> {
                        Optional<String> path = entity.getPath(parameterName);
                        if (path.isPresent()) {
                            return path.get();
                        } else {
                            matchContext.fail("Cannot perform batch update for non-existent property: " + parameterName);
                            return null;
                        }
                    })
                    .collect(Collectors.toList());

            Set<String> autoPopulateProperties = entity.getPersistentProperties()
                    .stream()
                    .filter(p -> p.findAnnotation(AutoPopulated.class).map(ap -> ap.getRequiredValue(AutoPopulated.UPDATEABLE, Boolean.class)).orElse(false))
                    .map(PersistentProperty::getName)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (CollectionUtils.isEmpty(updateProperties) && CollectionUtils.isEmpty(autoPopulateProperties)) {
                matchContext.fail("At least one parameter required to update");
                return null;
            }

            autoPopulateProperties.removeAll(updateProperties);

            return new MethodMatchInfo(
                    queryResultType,
                    query,
                    getInterceptorElement(matchContext, UpdateMethod.pickUpdateInterceptor(matchContext.getReturnType())),
                    MethodMatchInfo.OperationType.UPDATE,
                    updateProperties.toArray(new String[0]),
                    autoPopulateProperties.toArray(new String[0])
            );
        }
        return new MethodMatchInfo(
                queryResultType,
                query,
                getInterceptorElement(matchContext, UpdateMethod.pickUpdateInterceptor(matchContext.getReturnType())),
                MethodMatchInfo.OperationType.UPDATE);

    }
}
