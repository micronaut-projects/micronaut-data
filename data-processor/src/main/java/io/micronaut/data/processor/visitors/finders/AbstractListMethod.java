/*
 * Copyright 2017-2019 original authors
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
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.Sort;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.AnnotationMetadataHierarchy;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        QueryModel query = null;
        MethodElement methodElement = matchContext.getMethodElement();
        SourcePersistentEntity rootEntity = matchContext.getRootEntity();
        ClassElement queryResultType = rootEntity.getClassElement();

        if (methodElement.hasAnnotation(Query.class)) {
            query = buildRawQuery(matchContext);
        } else {
            if (CollectionUtils.isNotEmpty(queryParams)) {
                query = QueryModel.from(rootEntity);
                for (ParameterElement queryParam : queryParams) {
                    String paramName = queryParam.getName();
                    PersistentProperty prop = ((PersistentEntity) rootEntity).getPropertyByName(paramName);
                    if (prop == null) {
                        SourcePersistentProperty identity = rootEntity.getIdentity();
                        if (identity != null && identity.getName().equals(paramName)) {
                            query.idEq(new QueryParameter(queryParam.getName()));
                        } else {
                            matchContext.fail(
                                    "Cannot query entity [" + ((PersistentEntity) rootEntity).getSimpleName() + "] on non-existent property: " + paramName);
                            return null;
                        }
                    } else {
                        query.eq(prop.getName(), new QueryParameter(queryParam.getName()));
                    }
                }

            }

            String methodName = methodElement.getName();
            Matcher matcher = pattern.matcher(methodName);

            if (matcher.find()) {
                String querySequence = matcher.group(3);
                ArrayList<Sort.Order> orderBys = new ArrayList<>(2);
                querySequence = matchOrder(querySequence, orderBys);
                if (CollectionUtils.isNotEmpty(orderBys)) {
                    if (query == null) {
                        query = QueryModel.from(rootEntity);
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
                            query = QueryModel.from(rootEntity);
                        }

                        for (ProjectionMethodExpression projection : projections) {
                            projection.apply(matchContext, query);
                            queryResultType = projection.getExpectedResultType();
                        }

                        if (projections.size() > 1) {
                            queryResultType = matchContext.getVisitorContext().getClassElement(Object.class).orElse(rootEntity.getClassElement());
                        }

                        for (QueryModel.Projection projection : query.getProjections()) {
                            if (projection instanceof QueryModel.PropertyProjection) {
                                QueryModel.PropertyProjection pp = (QueryModel.PropertyProjection) projection;
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

            List<AnnotationValue<Join>> joinSpecs = matchContext.getAnnotationMetadata().getAnnotationValuesByType(Join.class);
            if (CollectionUtils.isNotEmpty(joinSpecs)) {
                if (query == null) {
                    query = QueryModel.from(rootEntity);
                }
                if (applyJoinSpecs(matchContext, query, rootEntity, joinSpecs)) {
                    return null;
                }
            }
        }


        if (query != null) {
            return buildInfo(matchContext, queryResultType, query);
        } else {
            if (matchContext.supportsImplicitQueries() && hasNoWhereDeclaration(matchContext)) {
                return buildInfo(matchContext, queryResultType, null);
            } else {
                return buildInfo(matchContext, queryResultType, QueryModel.from(rootEntity));
            }
        }
    }

    private boolean hasNoWhereDeclaration(@NonNull MethodMatchContext matchContext) {
        final boolean repositoryHasWhere = new AnnotationMetadataHierarchy(matchContext.getRepositoryClass(), matchContext.getMethodElement()).hasAnnotation(Where.class);
        final boolean entityHasWhere = matchContext.getRootEntity().hasAnnotation(Where.class);
        return !repositoryHasWhere && !entityHasWhere;
    }

    private static Pattern computePattern(String[] prefixes) {
        String prefixPattern = String.join("|", prefixes);
        return Pattern.compile("^((" + prefixPattern + ")(\\S*?))$");
    }

}
