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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.FindByIdInterceptor;
import io.micronaut.data.intercept.FindOneInterceptor;
import io.micronaut.data.intercept.async.FindByIdAsyncInterceptor;
import io.micronaut.data.intercept.async.FindOneAsyncInterceptor;
import io.micronaut.data.intercept.reactive.FindByIdReactiveInterceptor;
import io.micronaut.data.intercept.reactive.FindOneReactiveInterceptor;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.AssociationQuery;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.factory.Projections;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A method candidate based on pattern matching.
 *
 * @author graemerocher
 * @since 1.1.0
 */
public abstract class AbstractPatternBasedMethod implements MethodCandidate {

    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("(.*)OrderBy([\\w\\d]+)");
    private static final Pattern FOR_UPDATE_PATTERN = Pattern.compile("(.*)ForUpdate$");
    protected final Pattern pattern;

    /**
     * Default constructor.
     *
     * @param pattern The pattern to match
     */
    protected AbstractPatternBasedMethod(@NonNull Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean isMethodMatch(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {
        return pattern.matcher(methodElement.getName()).find();
    }

    /**
     * Matches order by definitions in the query sequence.
     *
     * @param querySequence The query sequence
     * @param orders        A list or orders to populate
     * @return The new query sequence minus any order by definitions
     */
    protected String matchOrder(String querySequence, List<Sort.Order> orders) {
        if (ORDER_BY_PATTERN.matcher(querySequence).matches()) {

            Matcher matcher = ORDER_BY_PATTERN.matcher(querySequence);
            StringBuffer buffer = new StringBuffer();
            if (matcher.find()) {
                matcher.appendReplacement(buffer, "$1");
                String orderDefGroup = matcher.group(2);
                if (StringUtils.isNotEmpty(orderDefGroup)) {
                    String[] orderDefItems = orderDefGroup.split("And");
                    for (String orderDef : orderDefItems) {
                        String prop = NameUtils.decapitalize(orderDef);
                        if (prop.endsWith("Desc")) {
                            orders.add(Sort.Order.desc(prop.substring(0, prop.length() - 4)));
                        } else if (prop.endsWith("Asc")) {
                            orders.add(Sort.Order.asc(prop.substring(0, prop.length() - 3)));
                        } else {
                            orders.add(Sort.Order.asc(prop));
                        }
                    }
                }
            }
            matcher.appendTail(buffer);
            return buffer.toString();
        }
        return querySequence;
    }

    /**
     * Matches for update definitions in the query sequence.
     *
     * @param matchContext
     * @param querySequence The query sequence
     * @return The new query sequence without the for update definitions
     */
    protected String matchForUpdate(MethodMatchContext matchContext, String querySequence) {
        if (matchContext.getQueryBuilder().supportsForUpdate()) {
            Matcher matcher = FOR_UPDATE_PATTERN.matcher(querySequence);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return querySequence;
    }

    /**
     * Matches projections.
     *
     * @param matchContext          The match context
     * @param projectionExpressions the projection expressions
     * @param projectionSequence    The sequence
     */
    protected void matchProjections(@NonNull MethodMatchContext matchContext, List<ProjectionMethodExpression> projectionExpressions, String projectionSequence) {
        ProjectionMethodExpression currentExpression = ProjectionMethodExpression.matchProjection(
                matchContext,
                projectionSequence
        );

        if (currentExpression != null) {
            // add to list of expressions
            projectionExpressions.add(currentExpression);
        }
    }

    /**
     * Build the {@link MethodMatchInfo}.
     *
     * @param matchContext    The match context
     * @param queryResultType The query result type
     * @param query           The query
     * @return The info or null if it can't be built
     */
    @Nullable
    protected MethodMatchInfo buildInfo(
            @NonNull MethodMatchContext matchContext,
            @NonNull ClassElement queryResultType,
            @Nullable QueryModel query) {
        ClassElement returnType = matchContext.getReturnType();
        if (!TypeUtils.isVoid(returnType)) {

            Map.Entry<ClassElement, Class<? extends DataInterceptor>> entry = FindersUtils.resolveFindInterceptor(matchContext, returnType);
            ClassElement resultType = entry.getKey();
            Class<? extends DataInterceptor> interceptorType = entry.getValue();

            if (interceptorType == FindOneInterceptor.class && isFindByIdQuery(matchContext, queryResultType, query)) {
                interceptorType = FindByIdInterceptor.class;
            } else if (interceptorType == FindOneAsyncInterceptor.class && isFindByIdQuery(matchContext, queryResultType, query)) {
                interceptorType = FindByIdAsyncInterceptor.class;
            } else if (interceptorType == FindOneReactiveInterceptor.class && isFindByIdQuery(matchContext, queryResultType, query)) {
                interceptorType = FindByIdReactiveInterceptor.class;
            }
            boolean isDto = false;
            if (resultType == null || TypeUtils.areTypesCompatible(resultType, queryResultType)) {
                if (!queryResultType.isPrimitive() || resultType == null) {
                    resultType = queryResultType;
                }
            } else {
                if (resultType.hasStereotype(Introspected.class) && queryResultType.hasStereotype(MappedEntity.class)) {
                    if (query == null) {
                        query = QueryModel.from(matchContext.getRootEntity());
                    }
                    if (!ignoreAttemptProjection(query)) {
                        attemptProjection(matchContext, queryResultType, query, resultType);
                    }
                    isDto = true;
                } else {
                    matchContext.failAndThrow("Query results in a type [" + queryResultType.getName() + "] whilst method returns an incompatible type: " + resultType.getName());
                }
            }
            return new MethodMatchInfo(resultType, query, FindersUtils.getInterceptorElement(matchContext, interceptorType), isDto);
        }

        matchContext.fail("Unsupported Repository method return type");
        return null;
    }

    /**
     * Obtain the interceptor element for the given class.
     *
     * @param matchContext The match context
     * @param type         The type
     * @return The element
     */
    protected ClassElement getInterceptorElement(@NonNull MethodMatchContext matchContext, Class<? extends DataInterceptor> type) {
        return FindersUtils.getInterceptorElement(matchContext, type);
    }

    /**
     * Obtain the interceptor element for the given class name.
     *
     * @param matchContext The match context
     * @param type         The type
     * @return The element
     */
    protected ClassElement getInterceptorElement(@NonNull MethodMatchContext matchContext, String type) {
        return FindersUtils.getInterceptorElement(matchContext, type);
    }

    /**
     * @param matchContext    Match Context
     * @param queryResultType Query Result Type
     * @param query           Query
     * @param returnType      Return Type
     * @return returns {@literal false} if the attempt to create the projection fails.
     */
    private void attemptProjection(@NonNull MethodMatchContext matchContext,
                                   @NonNull ClassElement queryResultType,
                                   @NonNull QueryModel query,
                                   ClassElement returnType) {
        List<PropertyElement> beanProperties = returnType.getBeanProperties();
        SourcePersistentEntity entity = matchContext.getEntity(queryResultType);
        for (PropertyElement beanProperty : beanProperties) {
            String propertyName = beanProperty.getName();
            if ("metaClass".equals(propertyName) && beanProperty.getType().isAssignable("groovy.lang.MetaClass")) {
                // ignore Groovy meta class
                continue;
            }
            SourcePersistentProperty pp = entity.getPropertyByName(propertyName);

            if (pp == null) {
                pp = entity.getIdOrVersionPropertyByName(propertyName);
            }

            if (pp == null) {
                matchContext.failAndThrow("Property " + propertyName + " is not present in entity: " + entity.getName());
                return;
            }

            if (!TypeUtils.areTypesCompatible(beanProperty.getType(), pp.getType())) {
                matchContext.failAndThrow("Property [" + propertyName + "] of type [" + beanProperty.getType().getName() + "] is not compatible with equivalent property declared in entity: " + entity.getName());
                return;
            }
            // add an alias projection for each property
            final QueryBuilder queryBuilder = matchContext.getQueryBuilder();
            if (queryBuilder.shouldAliasProjections()) {
                query.projections().add(Projections.property(propertyName).aliased());
            } else {
                query.projections().add(Projections.property(propertyName));
            }
        }
    }

    /**
     * Apply ordering.
     *
     * @param context   The context
     * @param query     The query
     * @param orderList The list mutate
     * @return True if an error occurred applying the order
     */
    protected boolean applyOrderBy(@NonNull MethodMatchContext context, @NonNull QueryModel query, @NonNull List<Sort.Order> orderList) {
        if (CollectionUtils.isNotEmpty(orderList)) {
            SourcePersistentEntity entity = context.getRootEntity();
            for (Sort.Order order : orderList) {
                String prop = order.getProperty();
                if (!entity.getPath(prop).isPresent()) {
                    context.fail("Cannot order by non-existent property: " + prop);
                    return true;
                }
            }
            query.sort(Sort.of(orderList));
        }
        return false;
    }

    /**
     * Apply for update.
     *
     * @param query The query
     */
    protected void applyForUpdate(QueryModel query) {
        query.forUpdate();
    }

    /**
     * @param matchContext The match context
     * @return a List of annotations values for {@Join} annotation.
     */
    @NonNull
    protected List<AnnotationValue<Join>> joinSpecsAtMatchContext(@NonNull MethodMatchContext matchContext) {
        final MethodMatchInfo.OperationType operationType = getOperationType();
        List<AnnotationValue<Join>> joins;
        if (operationType != MethodMatchInfo.OperationType.QUERY) {
            return matchContext.getAnnotationMetadata().getDeclaredAnnotationValuesByType(Join.class);
        }
        joins = matchContext.getAnnotationMetadata().getAnnotationValuesByType(Join.class);
        if (!joins.isEmpty()) {
            return joins;
        }
        return matchContext.getRepositoryClass().getAnnotationMetadata().getAnnotationValuesByType(Join.class);
    }

    /**
     * Apply the configured join specifications to the given query.
     *
     * @param matchContext The match context
     * @param query        The query
     * @param rootEntity   the root entity
     * @param joinSpecs    The join specs
     * @return True if an error occurred applying the specs
     */
    protected boolean applyJoinSpecs(
            @NonNull MethodMatchContext matchContext,
            @NonNull QueryModel query,
            @Nonnull SourcePersistentEntity rootEntity,
            @NonNull List<AnnotationValue<Join>> joinSpecs) {
        for (AnnotationValue<Join> joinSpec : joinSpecs) {
            String path = joinSpec.stringValue().orElse(null);
            Join.Type type = joinSpec.enumValue("type", Join.Type.class).orElse(Join.Type.FETCH);
            String alias = joinSpec.stringValue("alias").orElse(null);
            if (path != null) {
                PersistentProperty prop = rootEntity.getPropertyByPath(path).orElse(null);
                if (!(prop instanceof Association)) {
                    matchContext.fail("Invalid join spec [" + path + "]. Property is not an association!");
                    return true;
                } else {
                    boolean hasExisting = query.getCriteria().getCriteria().stream().anyMatch(c -> {
                        if (c instanceof AssociationQuery) {
                            AssociationQuery aq = (AssociationQuery) c;
                            return aq.getAssociation().equals(prop);
                        }
                        return false;
                    });
                    if (!hasExisting) {
                        query.add(new AssociationQuery(path, (Association) prop));
                    }
                    query.join(path, (Association) prop, type, alias);
                }
            }
        }
        return false;
    }

    private boolean isFindByIdQuery(@NonNull MethodMatchContext matchContext, @NonNull ClassElement queryResultType, @Nullable QueryModel query) {
        return matchContext.supportsImplicitQueries() && query != null && queryResultType.getName().equals(matchContext.getRootEntity().getName()) &&
                isIdEquals(query);
    }

    private Boolean isIdEquals(@NonNull QueryModel query) {
        List<QueryModel.Criterion> criteria = query.getCriteria().getCriteria();
        return criteria.size() == 1 && criteria.stream().findFirst().map(c -> c instanceof QueryModel.IdEquals).orElse(false);
    }

    /**
     * @return The operation type
     */
    protected @NonNull
    MethodMatchInfo.OperationType getOperationType() {
        return MethodMatchInfo.OperationType.QUERY;
    }

    private boolean ignoreAttemptProjection(@Nullable QueryModel query) {
        return query instanceof RawQuery;
    }

}
