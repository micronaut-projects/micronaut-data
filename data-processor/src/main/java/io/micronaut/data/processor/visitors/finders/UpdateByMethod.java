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
import io.micronaut.data.processor.model.SourcePersistentProperty;
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
        final SourcePersistentEntity rootEntity = matchContext.getRootEntity();
        SourcePersistentProperty version = rootEntity.getVersion();
        QueryParameter versionMatchParameter = null;
        List<String> updateProperties;
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
                        QueryParameter queryParameter = (QueryParameter) v;
                        queryParameters.add(queryParameter.getName());
                        if (criterion instanceof QueryModel.VersionEquals) {
                           versionMatchParameter = queryParameter;
                        }
                    }
                }
            }
            List<Element> updateParameters = matchContext.getParametersNotInRole().stream().filter(p -> !queryParameters.contains(p.getName()))
                    .collect(Collectors.toList());
            if (CollectionUtils.isEmpty(updateParameters)) {
                matchContext.fail("At least one parameter required to update");
                return null;
            }
            Element lastUpdatedProperty = matchContext.getParametersInRole().get(TypeRole.LAST_UPDATED_PROPERTY);
            if (lastUpdatedProperty instanceof PropertyElement) {
                updateParameters.add(lastUpdatedProperty);
            }
            SourcePersistentEntity entity = matchContext.getRootEntity();
            updateProperties = new ArrayList<>(updateParameters.size() + 1);
            for (Element parameter : updateParameters) {
                String parameterName = parameter.stringValue(Parameter.class).orElse(parameter.getName());
                Optional<String> path = entity.getPath(parameterName);
                if (path.isPresent()) {
                    updateProperties.add(path.get());
                } else {
                    matchContext.fail("Cannot perform batch update for non-existent property: " + parameterName);
                    return null;
                }
            }
            if (versionMatchParameter != null && !updateProperties.contains(version.getName())) {
                updateProperties.add(version.getName());
            }
        } else {
            updateProperties = Collections.emptyList();
        }
        MethodMatchInfo methodMatchInfo =  new MethodMatchInfo(
                queryResultType,
                query,
                getInterceptorElement(matchContext, UpdateMethod.pickUpdateInterceptor(matchContext.getReturnType())),
                MethodMatchInfo.OperationType.UPDATE,
                updateProperties.toArray(new String[0])
        );
        if (versionMatchParameter != null) {
            methodMatchInfo.setOptimisticLock(true);
            methodMatchInfo.addParameterRole(TypeRole.VERSION_UPDATE, VERSION_UPDATE_PARAMETER);
            methodMatchInfo.addQueryToMethodParameterBinding(version.getName(), VERSION_UPDATE_PARAMETER);
        }
        return methodMatchInfo;
    }
}
