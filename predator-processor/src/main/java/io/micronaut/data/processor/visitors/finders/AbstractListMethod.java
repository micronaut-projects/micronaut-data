/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.intercept.FindOneInterceptor;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.query.Sort;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * An abstract list based method.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public abstract class AbstractListMethod extends AbstractPatternBasedMethod {

    /**
     * Default constructor.
     * @param prefixes The method prefixes to match
     */
    protected AbstractListMethod(String...prefixes) {
        super(computePattern(prefixes));
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        List<ParameterElement> queryParams = matchContext.getParametersNotInRole();
        Query query = null;
        SourcePersistentEntity rootEntity = matchContext.getRootEntity();
        if (CollectionUtils.isNotEmpty(queryParams)) {
            query = Query.from(rootEntity);
            for (ParameterElement queryParam : queryParams) {
                String paramName = queryParam.getName();
                PersistentProperty prop = ((PersistentEntity) rootEntity).getPropertyByName(paramName);
                if (prop == null) {
                    SourcePersistentProperty identity = rootEntity.getIdentity();
                    if (identity != null && identity.getName().equals(paramName)) {
                        query.idEq(new QueryParameter(queryParam.getName()));
                    } else {
                        matchContext.getVisitorContext().fail(
                                "Cannot query entity [" + ((PersistentEntity) rootEntity).getSimpleName() + "] on non-existent property: " + paramName,
                                queryParam
                        );
                        return null;
                    }
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
                if (applyOrderBy(matchContext, query, orderBys)) {
                    return null;
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

    private static Pattern computePattern(String[] prefixes) {
        String prefixPattern = String.join("|", prefixes);
        return Pattern.compile("^((" + prefixPattern + ")(\\S*?))$");
    }

}
