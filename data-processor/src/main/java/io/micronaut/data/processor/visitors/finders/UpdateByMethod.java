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
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeRole;
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
        return super.isMethodMatch(methodElement, matchContext) && TypeUtils.isValidBatchUpdateReturnType(methodElement);
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
        String[] updateProperties;
        if (!(query instanceof RawQuery)) {

            List<QueryModel.Criterion> criterionList = query.getCriteria().getCriteria();
            if (CollectionUtils.isEmpty(criterionList)) {
                matchContext.fail("Cannot implement batch update operation that doesn't perform a query");
                return null;
            }
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
            List<Element> updateParameters = Arrays.stream(matchContext.getParameters()).filter(p -> !queryParameters.contains(p.getName()))
                    .collect(Collectors.toList());
            if (CollectionUtils.isEmpty(updateParameters)) {
                matchContext.fail("At least one parameter required to update");
                return null;
            }
            Element element = matchContext.getParametersInRole().get(TypeRole.LAST_UPDATED_PROPERTY);
            if (element instanceof PropertyElement) {
                updateParameters.add(element);
            }
            SourcePersistentEntity entity = matchContext.getRootEntity();
            updateProperties = new String[updateParameters.size()];
            for (int i = 0; i < updateProperties.length; i++) {
                Element parameter = updateParameters.get(i);
                String parameterName = parameter.stringValue(Parameter.class).orElse(parameter.getName());
                Optional<String> path = entity.getPath(parameterName);
                if (path.isPresent()) {
                    updateProperties[i] = path.get();
                } else {
                    matchContext.fail("Cannot perform batch update for non-existent property: " + parameterName);
                    return null;
                }
            }
        } else {
            updateProperties = StringUtils.EMPTY_STRING_ARRAY;
        }
        return new MethodMatchInfo(
                queryResultType,
                query,
                getInterceptorElement(matchContext, UpdateMethod.pickUpdateInterceptor(matchContext.getReturnType())),
                MethodMatchInfo.OperationType.UPDATE,
                updateProperties
        );
    }
}
