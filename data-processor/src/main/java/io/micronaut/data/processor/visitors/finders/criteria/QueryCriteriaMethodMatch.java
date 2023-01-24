/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.processor.visitors.finders.criteria;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.QueryModelPersistentEntityCriteriaQuery;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaQuery;
import io.micronaut.data.processor.model.criteria.impl.MethodMatchSourcePersistentEntityCriteriaBuilderImpl;
import io.micronaut.data.processor.visitors.AnnotationMetadataHierarchy;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.AbstractCriteriaMethodMatch;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.Projections;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Query criteria method match.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public class QueryCriteriaMethodMatch extends AbstractCriteriaMethodMatch {
    private static final String[] ORDER_VARIATIONS = {"Order", "Sort"};
    private static final String BY = "By";
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("(.*)(" + Arrays.stream(ORDER_VARIATIONS).map(o -> o + BY).collect(Collectors.joining("|")) + ")([\\w\\d]+)");

    /**
     * Default constructor.
     *
     * @param matcher The matcher
     */
    public QueryCriteriaMethodMatch(Matcher matcher) {
        super(matcher);
    }

    /**
     * Apply query match.
     *
     * @param matchContext The match context
     * @param root         The root
     * @param query        The query
     * @param cb           The criteria builder
     * @param <T>          The entity type
     */
    protected <T> void apply(MethodMatchContext matchContext,
                             PersistentEntityRoot<T> root,
                             PersistentEntityCriteriaQuery<T> query,
                             SourcePersistentEntityCriteriaBuilder cb) {
        if (matcher.groupCount() == 4) {
            String projectionSequence = matcher.group(3);
            String querySequence = matcher.group(4);
            for (String orderVariation : ORDER_VARIATIONS) {
                if (projectionSequence.endsWith(orderVariation) && matchContext.getMethodElement().getName().contains(orderVariation + BY + querySequence)) {
                    apply(matchContext, root, query, cb, projectionSequence + BY + querySequence);
                    return;
                }
            }
            apply(matchContext, root, query, cb, projectionSequence, querySequence);
        } else if (matcher.group(2).endsWith(BY)) {
            apply(matchContext, root, query, cb, "", matcher.group(3));
        } else {
            String querySequence = matcher.group(3);
            apply(matchContext, root, query, cb, querySequence);
        }
    }

    private <T> void apply(MethodMatchContext matchContext,
                           PersistentEntityRoot<T> root,
                           PersistentEntityCriteriaQuery<T> query,
                           SourcePersistentEntityCriteriaBuilder cb,
                           String projectionSequence) {
        applyPredicates(matchContext.getParametersNotInRole(), root, query, cb);
        projectionSequence = applyForUpdate(projectionSequence, query);
        projectionSequence = applyOrderBy(projectionSequence, root, query, cb);
        projectionSequence = applyProjections(projectionSequence, root, query, cb);
        applyProjectionLimits(projectionSequence, matchContext, query);
        applyJoinSpecs(root, joinSpecsAtMatchContext(matchContext, true));
    }

    private <T> void apply(MethodMatchContext matchContext,
                           PersistentEntityRoot<T> root,
                           PersistentEntityCriteriaQuery<T> query,
                           SourcePersistentEntityCriteriaBuilder cb,
                           String projectionSequence,
                           String querySequence) {

        projectionSequence = applyProjections(projectionSequence, root, query, cb);
        applyProjectionLimits(projectionSequence, matchContext, query);

        querySequence = applyForUpdate(querySequence, query);
        querySequence = applyOrderBy(querySequence, root, query, cb);
        applyPredicates(querySequence, matchContext.getParameters(), root, query, cb);

        applyJoinSpecs(root, joinSpecsAtMatchContext(matchContext, true));
    }

    @Override
    protected MethodMatchInfo build(MethodMatchContext matchContext) {
        ClassElement queryResultType = matchContext.getRootEntity().getClassElement();

        MethodMatchSourcePersistentEntityCriteriaBuilderImpl cb = new MethodMatchSourcePersistentEntityCriteriaBuilderImpl(matchContext);

        PersistentEntityCriteriaQuery<Object> criteriaQuery = cb.createQuery();
        apply(matchContext, criteriaQuery.from(matchContext.getRootEntity()), criteriaQuery, cb);

        Map.Entry<ClassElement, Class<? extends DataInterceptor>> entry = resolveReturnTypeAndInterceptor(matchContext);
        ClassElement resultType = entry.getKey();
        Class<? extends DataInterceptor> interceptorType = entry.getValue();

        boolean optimisticLock = ((AbstractPersistentEntityCriteriaQuery<?>) criteriaQuery).hasVersionRestriction();

        SourcePersistentEntityCriteriaQuery<?> query = (SourcePersistentEntityCriteriaQuery) criteriaQuery;
        String selectedType = query.getQueryResultTypeName();
        if (selectedType != null) {
            queryResultType = matchContext.getVisitorContext().getClassElement(selectedType)
                    .orElse(null);
            if (queryResultType == null) {
                try {
                    queryResultType = PrimitiveElement.valueOf(selectedType);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        boolean isDto = resultType != null
                && !TypeUtils.areTypesCompatible(resultType, queryResultType)
                && (isDtoType(resultType) || resultType.hasStereotype(Introspected.class) && queryResultType.hasStereotype(MappedEntity.class));

        if (isDto) {
            if (!isDtoType(resultType)) {
                List<SourcePersistentProperty> dtoProjectionProperties = getDtoProjectionProperties(matchContext.getRootEntity(), resultType);
                if (!dtoProjectionProperties.isEmpty()) {
                    Root<?> root = query.getRoots().iterator().next();
                    query.multiselect(
                            dtoProjectionProperties.stream()
                                    .map(p -> {
                                        if (matchContext.getQueryBuilder().shouldAliasProjections()) {
                                            return root.get(p.getName()).alias(p.getName());
                                        } else {
                                            return root.get(p.getName());
                                        }
                                    })
                                    .collect(Collectors.toList())
                    );
                }
            }
        } else {
            if (resultType == null || (!resultType.isAssignable(void.class) && !resultType.isAssignable(Void.class))) {
                if (resultType == null || TypeUtils.areTypesCompatible(resultType, queryResultType)) {
                    if (!queryResultType.isPrimitive() || resultType == null) {
                        resultType = queryResultType;
                    }
                } else {
                    throw new MatchFailedException("Query results in a type [" + queryResultType.getName() + "] whilst method returns an incompatible type: " + resultType.getName());
                }
            }
        }

        final AnnotationMetadataHierarchy annotationMetadataHierarchy = new AnnotationMetadataHierarchy(
                matchContext.getRepositoryClass().getAnnotationMetadata(),
                matchContext.getAnnotationMetadata()
        );
        QueryBuilder queryBuilder = matchContext.getQueryBuilder();
        QueryModel queryModel = ((QueryModelPersistentEntityCriteriaQuery) criteriaQuery).getQueryModel();
        QueryResult queryResult = queryBuilder.buildQuery(annotationMetadataHierarchy, queryModel);

        ClassElement genericReturnType = matchContext.getReturnType();
        if (TypeUtils.isReactiveOrFuture(genericReturnType)) {
            genericReturnType = genericReturnType.getFirstTypeArgument().orElse(matchContext.getRootEntity().getType());
        }
        QueryResult countQueryResult = null;
        if (matchContext.isTypeInRole(genericReturnType, TypeRole.PAGE)) {
//                SourcePersistentEntityCriteriaQuery<Object> count = cb.createQuery();
//                count.select(cb.count(query.getRoots().iterator().next()));
//                CommonAbstractCriteria countQueryCriteria = defineQuery(matchContext, matchContext.getRootEntity(), cb);

            QueryModel countQuery = QueryModel.from(queryModel.getPersistentEntity());
            countQuery.projections().count();
            QueryModel.Junction junction = queryModel.getCriteria();
            for (QueryModel.Criterion criterion : junction.getCriteria()) {
                countQuery.add(criterion);
            }
            // Joins are skipped for count query for OneToMany, ManyToMany
            // however skipping joins from criteria could cause issues (in many to many?)
            for (JoinPath joinPath : queryModel.getJoinPaths()) {
                Association association = joinPath.getAssociation();
                if (association != null && !association.getKind().isSingleEnded()) {
                    // skip OneToMany and ManyToMany
                    continue;
                }
                Join.Type joinType = joinPath.getJoinType();
                switch (joinType) {
                    case INNER:
                    case FETCH:
                        joinType = Join.Type.DEFAULT;
                        break;
                    case LEFT_FETCH:
                        joinType = Join.Type.LEFT;
                        break;
                    case RIGHT_FETCH:
                        joinType = Join.Type.RIGHT;
                        break;
                    default:
                        // no-op
                }
                countQuery.join(joinPath.getPath(), joinType, null);
            }
            countQueryResult = queryBuilder.buildQuery(countQuery);
        }

        return new MethodMatchInfo(
                DataMethod.OperationType.QUERY,
                resultType,
                getInterceptorElement(matchContext, interceptorType)
        )
                .dto(isDto)
                .optimisticLock(optimisticLock)
                .queryResult(queryResult)
                .countQueryResult(countQueryResult);
    }

    private boolean isDtoType(ClassElement classElement) {
        return classElement.getName().equals("org.bson.BsonDocument");
    }

    private List<SourcePersistentProperty> getDtoProjectionProperties(SourcePersistentEntity entity,
                                                                      ClassElement returnType) {
        return returnType.getBeanProperties().stream()
                .filter(dtoProperty -> {
                    String propertyName = dtoProperty.getName();
                    // ignore Groovy meta class
                    return !"metaClass".equals(propertyName) || !dtoProperty.getType().isAssignable("groovy.lang.MetaClass");
                })
                .map(dtoProperty -> {
                    String propertyName = dtoProperty.getName();
                    if ("metaClass".equals(propertyName) && dtoProperty.getType().isAssignable("groovy.lang.MetaClass")) {
                        // ignore Groovy meta class
                        return null;
                    }
                    SourcePersistentProperty pp = entity.getPropertyByName(propertyName);

                    if (pp == null) {
                        pp = entity.getIdOrVersionPropertyByName(propertyName);
                    }

                    if (pp == null) {
                        throw new MatchFailedException("Property " + propertyName + " is not present in entity: " + entity.getName());
                    }

                    ClassElement dtoPropertyType = dtoProperty.getType();
                    if (dtoPropertyType.getName().equals("java.lang.Object") || dtoPropertyType.getName().equals("java.lang.String")) {
                        // Convert anything to a string or an object
                        return pp;
                    }
                    if (!TypeUtils.areTypesCompatible(dtoPropertyType, pp.getType())) {
                        throw new MatchFailedException("Property [" + propertyName + "] of type [" + dtoPropertyType.getName() + "] is not compatible with equivalent property of type [" + pp.getType().getName() + "] declared in entity: " + entity.getName());
                    }
                    return pp;
                }).collect(Collectors.toList());
    }

    private <T> void applyProjectionLimits(String querySequence, MatchContext matchContext, PersistentEntityCriteriaQuery<T> query) {
        if (StringUtils.isEmpty(querySequence)) {
            return;
        }
        String decapitalized = NameUtils.decapitalize(querySequence);
        if (!Arrays.asList("all", "one").contains(decapitalized)) {
            Matcher topMatcher = Pattern.compile("^(top|first)(\\d*)$").matcher(decapitalized);
            if (topMatcher.find()) {
                String str = topMatcher.group(2);
                try {
                    int max = StringUtils.isNotEmpty(str) ? Integer.parseInt(str) : 1;
                    if (max > -1) {
                        query.max(max);
                    }
                } catch (NumberFormatException e) {
                    throw new MatchFailedException("Invalid number specified to top: " + str);
                }
            } else {
                // if the return type simple name is the same then we assume this is ok
                // this allows for Optional findOptionalByName
                if (!querySequence.equals(matchContext.getReturnType().getSimpleName())) {
                    throw new MatchFailedException("Cannot project on non-existent property: " + decapitalized);
                }
            }
        }
    }

    private <T> String applyOrderBy(String querySequence,
                                    PersistentEntityRoot<T> root,
                                    PersistentEntityCriteriaQuery<T> query,
                                    PersistentEntityCriteriaBuilder cb) {
        if (ORDER_BY_PATTERN.matcher(querySequence).matches()) {
            List<Order> orders = new ArrayList<>();
            java.util.regex.Matcher matcher = ORDER_BY_PATTERN.matcher(querySequence);
            StringBuffer buffer = new StringBuffer();
            if (matcher.find()) {
                matcher.appendReplacement(buffer, "$1");
                String orderDefGroup = matcher.group(3);
                if (StringUtils.isNotEmpty(orderDefGroup)) {
                    String[] orderDefItems = orderDefGroup.split("And");
                    for (String orderDef : orderDefItems) {
                        String prop = NameUtils.decapitalize(orderDef);
                        if (prop.endsWith("Desc")) {
                            String propertyName = prop.substring(0, prop.length() - 4);
                            orders.add(cb.desc(findOrderProperty(root, propertyName)));
                        } else if (prop.endsWith("Asc")) {
                            String propertyName = prop.substring(0, prop.length() - 3);
                            orders.add(cb.asc(findOrderProperty(root, propertyName)));
                        } else {
                            orders.add(cb.asc(findOrderProperty(root, prop)));
                        }
                    }
                }
            }
            if (!orders.isEmpty()) {
                query.orderBy(orders);
            }
            matcher.appendTail(buffer);
            return buffer.toString();
        }
        return querySequence;
    }

    private <T> io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> findOrderProperty(PersistentEntityRoot<T> root, String propertyName) {
        if (root.getPersistentEntity().getPropertyByName(propertyName) != null) {
            return root.get(propertyName);
        }
        // Look at association paths
        io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> property = findProperty(root, propertyName);
        if (property != null) {
            return property;
        }
        throw new MatchFailedException("Cannot order by non-existent property: " + propertyName);
    }

    /**
     * Apply projections.
     *
     * @param querySequence The querySequence
     * @param root          The root
     * @param query         The query
     * @param cb            The criteria builder
     * @param <T>           The entity type
     * @return the remaining querySequence
     */
    protected <T> String applyProjections(String querySequence,
                                          PersistentEntityRoot<T> root,
                                          PersistentEntityCriteriaQuery<T> query,
                                          PersistentEntityCriteriaBuilder cb) {
        if (querySequence.startsWith("Distinct")) {
            query.distinct(true);
            querySequence = querySequence.substring("Distinct".length());
        }
        if (StringUtils.isNotEmpty(querySequence)) {
            List<Selection<?>> selectionList = new ArrayList<>();
            for (String projection : querySequence.split("And")) {
                io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath = findProperty(root, projection);
                if (propertyPath != null) {
                    selectionList.add(propertyPath);
                } else {
                    Selection<?> selection = Projections.find(root, cb, projection, this::findProperty);
                    if (selection != null) {
                        selectionList.add(selection);
                    }
                }
            }
            if (selectionList.isEmpty()) {
                return querySequence;
            }
            query.multiselect(selectionList);
            return "";
        }
        return querySequence;
    }

    @Override
    protected DataMethod.OperationType getOperationType() {
        return DataMethod.OperationType.QUERY;
    }
}
