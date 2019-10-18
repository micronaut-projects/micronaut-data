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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.*;
import io.micronaut.data.intercept.*;
import io.micronaut.data.intercept.async.*;
import io.micronaut.data.intercept.reactive.*;
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
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import org.reactivestreams.Publisher;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A method candidate based on pattern matching.
 *
 * @author graemerocher
 * @since 1.1.0
 */
public abstract class AbstractPatternBasedMethod implements MethodCandidate {

    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("(.*)OrderBy([\\w\\d]+)");
    protected final Pattern pattern;

    /**
     * Default constructor.
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
     * @param querySequence The query sequence
     * @param orders A list or orders to populate
     * @return The new query sequence minus any order by definitions
     */
    protected String matchOrder(String querySequence, List<Sort.Order> orders) {
        if (ORDER_BY_PATTERN.matcher(querySequence).matches()) {

            Matcher matcher = ORDER_BY_PATTERN.matcher(querySequence);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(buffer, "$1");
                String orderDef = matcher.group(2);
                if (StringUtils.isNotEmpty(orderDef)) {
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
            matcher.appendTail(buffer);
            return buffer.toString();
        }
        return querySequence;
    }

    /**
     * Matches projections.
     * @param matchContext The match context
     * @param projectionExpressions the projection expressions
     * @param projectionSequence The sequence
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
     * @param matchContext The match context
     * @param queryResultType The query result type
     * @param query The query
     * @return The info or null if it can't be built
     */
    @Nullable
    protected MethodMatchInfo buildInfo(
            @NonNull MethodMatchContext matchContext,
            @NonNull ClassElement queryResultType,
            @Nullable QueryModel query) {
        ClassElement returnType = matchContext.getReturnType();
        ClassElement typeArgument = returnType.getFirstTypeArgument().orElse(null);
        if (!returnType.getName().equals("void")) {
            if (isValidResultType(returnType)) {
                if (TypeUtils.areTypesCompatible(returnType, queryResultType)) {
                    if (isFindByIdQuery(matchContext, queryResultType, query)) {
                        return new MethodMatchInfo(
                                matchContext.getReturnType(),
                                query,
                                FindByIdInterceptor.class
                        );
                    } else {
                        return new MethodMatchInfo(queryResultType, query, FindOneInterceptor.class);
                    }
                } else {
                    if (query != null && returnType.hasStereotype(Introspected.class) && queryResultType.hasStereotype(MappedEntity.class)) {
                        if (attemptProjection(matchContext, queryResultType, query, returnType)) {
                            return null;
                        }

                        return new MethodMatchInfo(returnType, query, FindOneInterceptor.class, true);
                    } else {

                        matchContext.fail("Query results in a type [" + queryResultType.getName() + "] whilst method returns an incompatible type: " + returnType.getName());
                        return null;
                    }
                }
            } else if (typeArgument != null) {
                boolean isPage = matchContext.isTypeInRole(
                        typeArgument,
                        TypeRole.PAGE
                );
                boolean isSlice = matchContext.isTypeInRole(
                        typeArgument,
                        TypeRole.SLICE
                );
                if (returnType.isAssignable(CompletionStage.class) || returnType.isAssignable(Future.class)) {
                    Class<? extends DataInterceptor> interceptorType;
                    ClassElement firstTypeArgument;

                    if (typeArgument.isAssignable(Iterable.class) || isSlice || isPage) {
                        firstTypeArgument = typeArgument.getFirstTypeArgument().orElse(null);
                        if (firstTypeArgument == null) {
                            matchContext.fail("Async return type missing type argument");
                            return null;
                        }
                    } else {
                        firstTypeArgument = typeArgument;
                    }
                    if (isPage) {
                        interceptorType = FindPageAsyncInterceptor.class;
                    } else if (isSlice) {
                        interceptorType = FindSliceAsyncInterceptor.class;
                    } else if (typeArgument.isAssignable(Iterable.class)) {
                        interceptorType = FindAllAsyncInterceptor.class;
                    } else if (isValidResultType(typeArgument)) {
                        if (isFindByIdQuery(matchContext, queryResultType, query)) {
                            interceptorType = FindByIdAsyncInterceptor.class;
                        } else {
                            interceptorType = FindOneAsyncInterceptor.class;
                        }
                    } else {
                        matchContext.fail("Unsupported Async return type: " + firstTypeArgument.getName());
                        return null;
                    }
                    ClassElement finalResultType = firstTypeArgument;
                    if (TypeUtils.isObjectClass(finalResultType)) {
                        finalResultType = matchContext.getRootEntity().getType();
                    }
                    boolean dto = resolveDtoIfNecessary(matchContext, queryResultType, query, finalResultType);
                    if (matchContext.isFailing()) {
                        return null;
                    } else {
                        return new MethodMatchInfo(finalResultType, query, interceptorType, dto);
                    }
                } else if (returnType.isAssignable(Publisher.class) || returnType.getPackageName().equals("io.reactivex")) {
                    Class<? extends DataInterceptor> interceptorType;
                    ClassElement finalResultType = TypeUtils.isObjectClass(typeArgument) ? matchContext.getRootEntity().getType() : typeArgument;
                    boolean isContainerType = isSlice || isPage;
                    if (isContainerType) {
                        finalResultType = typeArgument.getFirstTypeArgument().orElse(matchContext.getRootEntity().getType());
                    }
                    if (isPage) {
                        interceptorType = FindPageReactiveInterceptor.class;
                    } else if (isSlice) {
                        interceptorType = FindSliceReactiveInterceptor.class;
                    } else {
                        if (isReactiveSingleResult(returnType)) {
                            if (isFindByIdQuery(matchContext, queryResultType, query)) {
                                interceptorType = FindByIdReactiveInterceptor.class;
                            } else {
                                interceptorType = FindOneReactiveInterceptor.class;
                            }
                        } else {
                            interceptorType = FindAllReactiveInterceptor.class;
                        }
                    }
                    boolean dto = resolveDtoIfNecessary(matchContext, queryResultType, query, finalResultType);
                    if (matchContext.isFailing()) {
                        return null;
                    } else {
                        return new MethodMatchInfo(finalResultType, query, interceptorType, dto);
                    }
                } else {
                    boolean dto = false;
                    if (!TypeUtils.areTypesCompatible(typeArgument, queryResultType)) {

                        if ((typeArgument.hasStereotype(Introspected.class) && queryResultType.hasStereotype(MappedEntity.class))) {
                            QueryModel projectionQuery = query != null ? query : QueryModel.from(matchContext.getRootEntity());
                            if (attemptProjection(matchContext, queryResultType, projectionQuery, typeArgument)) {
                                return null;
                            }
                            query = projectionQuery;
                            dto = true;
                        } else {
                            matchContext.fail("Query results in a type [" + queryResultType.getName() + "] whilst method returns an incompatible type: " + typeArgument.getName());
                            return null;
                        }
                    }

                    if (matchContext.isTypeInRole(
                            matchContext.getReturnType(),
                            TypeRole.PAGE
                    )) {
                        return new MethodMatchInfo(typeArgument, query, FindPageInterceptor.class, dto);
                    } else if (matchContext.isTypeInRole(
                            matchContext.getReturnType(),
                            TypeRole.SLICE
                    )) {
                        return new MethodMatchInfo(typeArgument, query, FindSliceInterceptor.class, dto);
                    } else if (returnType.isAssignable(Iterable.class)) {
                        return new MethodMatchInfo(typeArgument, query, FindAllInterceptor.class, dto);
                    } else if (returnType.isAssignable(Stream.class)) {
                        return new MethodMatchInfo(typeArgument, query, FindStreamInterceptor.class, dto);
                    } else if (returnType.isAssignable(Optional.class)) {
                        return new MethodMatchInfo(typeArgument, query, FindOptionalInterceptor.class, dto);
                    } else if (returnType.isAssignable(Publisher.class)) {
                        return new MethodMatchInfo(typeArgument, query, FindAllReactiveInterceptor.class, dto);
                    }
                }
            }
        }

        matchContext.fail("Unsupported Repository method return type");
        return null;
    }

    private boolean isFindByIdQuery(@NonNull MethodMatchContext matchContext, @NonNull ClassElement queryResultType, @Nullable QueryModel query) {
        return matchContext.supportsImplicitQueries() && query != null && queryResultType.getName().equals(matchContext.getRootEntity().getName()) &&
                isIdEquals(query);
    }

    private Boolean isIdEquals(@NonNull QueryModel query) {
        List<QueryModel.Criterion> criteria = query.getCriteria().getCriteria();
        return criteria.size() == 1 && criteria.stream().findFirst().map(c -> c instanceof QueryModel.IdEquals).orElse(false);
    }

    private boolean resolveDtoIfNecessary(
            @NonNull MethodMatchContext matchContext,
            @NonNull ClassElement queryResultType,
            @Nullable QueryModel query,
            @NonNull ClassElement returnType) {
        boolean dto = false;
        if (!TypeUtils.areTypesCompatible(returnType, queryResultType)) {
            if (query != null && returnType.hasStereotype(Introspected.class) && queryResultType.hasStereotype(MappedEntity.class)) {
                if (!attemptProjection(matchContext, queryResultType, query, returnType)) {
                    dto = true;
                }
            } else {
                matchContext.fail("Query results in a type [" + queryResultType.getName() + "] whilst method returns an incompatible type: " + returnType.getName());
            }
        }
        return dto;
    }

    private boolean isValidResultType(ClassElement returnType) {
        return returnType.hasStereotype(Introspected.class) || ClassUtils.isJavaBasicType(returnType.getName()) || returnType.isPrimitive();
    }

    private boolean isReactiveSingleResult(ClassElement returnType) {
        return returnType.hasStereotype(SingleResult.class) || returnType.isAssignable("io.reactivex.Single") || returnType.isAssignable("reactor.core.publisher.Mono");
    }

    private boolean attemptProjection(@NonNull MethodMatchContext matchContext, @NonNull ClassElement queryResultType, @NonNull QueryModel query, ClassElement returnType) {
        List<PropertyElement> beanProperties = returnType.getBeanProperties();
        SourcePersistentEntity entity = matchContext.getEntity(queryResultType);
        for (PropertyElement beanProperty : beanProperties) {
            String propertyName = beanProperty.getName();
            SourcePersistentProperty pp = entity.getPropertyByName(propertyName);

            if (pp == null) {
                pp = entity.getIdOrVersionPropertyByName(propertyName);
            }

            if (pp == null) {
                matchContext.fail("Property " + propertyName + " is not present in entity: " + entity.getName());
                return true;
            }

            if (!TypeUtils.areTypesCompatible(beanProperty.getType(), pp.getType())) {
                matchContext.fail("Property [" + propertyName + "] of type [" + beanProperty.getType().getName() + "] is not compatible with equivalent property declared in entity: " + entity.getName());
                return true;
            }
            // add an alias projection for each property
            query.projections().add(Projections.property(propertyName).aliased());
        }
        return false;
    }

    /**
     * Apply ordering.
     * @param context The context
     * @param query The query
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
     * Apply the configured join specifications to the given query.
     * @param matchContext The match context
     * @param query The query
     * @param rootEntity the root entity
     * @param joinSpecs The join specs
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

    /**
     * Builds a raw query for the given match context. Should be called for methods annotated with {@link Query} explicitly.
     * @param matchContext The match context
     * @return The raw query or null if an error occurred
     */
    protected RawQuery buildRawQuery(@NonNull MethodMatchContext matchContext) {
        MethodElement methodElement = matchContext.getMethodElement();
        String queryString = methodElement.stringValue(Query.class).orElseThrow(() ->
            new IllegalStateException("Should only be called if Query has value!")
        );
        List<ParameterElement> parameters = Arrays.asList(matchContext.getParameters());
        Map<String, String> parameterBinding = new LinkedHashMap<>(parameters.size());
        boolean namedParameters = matchContext.getRepositoryClass()
                .booleanValue(RepositoryConfiguration.class, "namedParameters").orElse(true);
        if (namedParameters) {
            Matcher matcher = QueryBuilder.VARIABLE_PATTERN.matcher(queryString);

            while (matcher.find()) {
                String name = matcher.group(2);
                Optional<ParameterElement> element = parameters.stream().filter(p -> p.getName().equals(name)).findFirst();
                if (element.isPresent()) {
                    parameterBinding.put(name, element.get().getName());
                } else {
                    matchContext.fail(
                            "No method parameter found for named Query parameter : " + name
                    );
                    return null;
                }
            }
        } else {
            Matcher matcher = QueryBuilder.VARIABLE_PATTERN.matcher(queryString);

            int index = 1;
            while (matcher.find()) {
                String name = matcher.group(2);
                Optional<ParameterElement> element = parameters.stream().filter(p -> p.getName().equals(name)).findFirst();
                if (element.isPresent()) {
                    parameterBinding.put(String.valueOf(index++), element.get().getName());
                } else {
                    matchContext.fail(
                            "No method parameter found for named Query parameter : " + name
                    );
                    return null;
                }
            }
        }
        return new RawQuery(matchContext.getRootEntity(), parameterBinding);
    }
}
