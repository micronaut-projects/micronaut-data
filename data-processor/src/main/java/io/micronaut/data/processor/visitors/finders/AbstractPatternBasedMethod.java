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
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
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
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
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
    private static final Pattern FOR_UPDATE_PATTERN = Pattern.compile("(.*)ForUpdate$");
    private static final String DELETE = "delete";
    private static final String UPDATE = "update";
    private static final String VOID = "void";
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
        ClassElement typeArgument = returnType.getFirstTypeArgument().orElse(null);

        if (!returnType.getName().equals(VOID)) {
            if (query instanceof RawQuery) {
                final AnnotationMetadata annotationMetadata = matchContext.getAnnotationMetadata();
                String q = annotationMetadata.stringValue(Query.class).orElse(null);
                if (q != null) {
                    final boolean readOnly = annotationMetadata
                            .booleanValue(Query.class, "readOnly").orElse(true);
                    q = q.trim().toLowerCase(Locale.ENGLISH);
                    Class<? extends DataInterceptor> interceptorType = null;
                    if (q.startsWith(DELETE)) {
                        interceptorType = resolveInterceptorTypeByOperationType(MethodMatchInfo.OperationType.DELETE, returnType);
                    } else if (q.startsWith(UPDATE) || !readOnly) {
                        interceptorType = resolveInterceptorTypeByOperationType(MethodMatchInfo.OperationType.UPDATE, returnType);
                    }
                    if (interceptorType != null) {
                        return new MethodMatchInfo(queryResultType, query, getInterceptorElement(matchContext, interceptorType));
                    }
                }
            }

            if (isValidResultType(returnType)) {
                if (TypeUtils.areTypesCompatible(returnType, queryResultType)) {
                    if (isFindByIdQuery(matchContext, queryResultType, query)) {
                        final Class<FindByIdInterceptor> type = FindByIdInterceptor.class;
                        return new MethodMatchInfo(matchContext.getReturnType(), query, getInterceptorElement(matchContext, type));
                    } else {
                        return new MethodMatchInfo(queryResultType, query, getInterceptorElement(matchContext, FindOneInterceptor.class));
                    }
                } else {
                    if (query != null && returnType.hasStereotype(Introspected.class) && queryResultType.hasStereotype(MappedEntity.class)) {
                        if (!ignoreAttemptProjection(query) && !attemptProjection(matchContext, queryResultType, query, returnType)) {
                            return null;
                        }

                        return new MethodMatchInfo(returnType, query, getInterceptorElement(matchContext, FindOneInterceptor.class), true);
                    } else {

                        matchContext.fail("Query results in a type [" + queryResultType.getName() + "] whilst method returns an incompatible type: " + returnType.getName());
                        return null;
                    }
                }
            } else if (typeArgument != null) {
                boolean isPage = isPage(matchContext, typeArgument);
                boolean isSlice = isSlice(matchContext, typeArgument);
                if (returnType.isAssignable(CompletionStage.class) || returnType.isAssignable(Future.class)) {

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

                    Class<? extends DataInterceptor> interceptorType = resolveInterceptorType(matchContext,
                            typeArgument, queryResultType, query, isPage, isSlice);
                   if (interceptorType == null) {
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
                        return new MethodMatchInfo(finalResultType, query, getInterceptorElement(matchContext, interceptorType), dto);
                    }
                } else if (returnType.isAssignable(Publisher.class) || returnType.getPackageName().equals("io.reactivex")) {

                    ClassElement finalResultType = TypeUtils.isObjectClass(typeArgument) ? matchContext.getRootEntity().getType() : typeArgument;
                    boolean isContainerType = isSlice || isPage;
                    if (isContainerType) {
                        finalResultType = typeArgument.getFirstTypeArgument().orElse(matchContext.getRootEntity().getType());
                    }
                    Class<? extends DataInterceptor> interceptorType = resolveReactiveInterceptorType(matchContext,
                            returnType, queryResultType, query, isPage, isSlice);

                    boolean dto = resolveDtoIfNecessary(matchContext, queryResultType, query, finalResultType);
                    if (matchContext.isFailing()) {
                        return null;
                    } else {
                        return new MethodMatchInfo(finalResultType, query, getInterceptorElement(matchContext, interceptorType), dto);
                    }
                } else {
                    boolean dto = false;
                    if (!TypeUtils.areTypesCompatible(typeArgument, queryResultType)) {

                        if ((typeArgument.hasStereotype(Introspected.class) && queryResultType.hasStereotype(MappedEntity.class))) {
                            QueryModel projectionQuery = query != null ? query : QueryModel.from(matchContext.getRootEntity());
                            if (!ignoreAttemptProjection(query) && !attemptProjection(matchContext, queryResultType, projectionQuery, typeArgument)) {
                                return null;
                            }
                            query = projectionQuery;
                            dto = true;
                        } else {
                            matchContext.fail("Query results in a type [" + queryResultType.getName() + "] whilst method returns an incompatible type: " + typeArgument.getName());
                            return null;
                        }
                    }
                    Class<? extends DataInterceptor> interceptor = resolveFindInterceptor(matchContext);
                    if (interceptor != null) {
                        return new MethodMatchInfo(typeArgument, query, getInterceptorElement(matchContext, interceptor), dto);
                    }
                }
            }
        }

        matchContext.fail("Unsupported Repository method return type");
        return null;
    }


    /**
     * @param matchContext The match context
     * @return The resolved {@link DataInterceptor} or {@literal null}.
     */
    @Nullable
    protected Class<? extends DataInterceptor> resolveFindInterceptor(@NonNull MethodMatchContext matchContext) {
        ClassElement returnType = matchContext.getReturnType();

        if (isPage(matchContext, returnType)) {
            return FindPageInterceptor.class;
        } else if (isSlice(matchContext, returnType)) {
            return FindSliceInterceptor.class;
        } else if (returnType.isAssignable(Iterable.class)) {
            return FindAllInterceptor.class;
        } else if (returnType.isAssignable(Stream.class)) {
            return FindStreamInterceptor.class;
        } else if (returnType.isAssignable(Optional.class)) {
            return FindOptionalInterceptor.class;
        } else if (returnType.isAssignable(Publisher.class)) {
            return FindAllReactiveInterceptor.class;
        }
        return null;
    }

    /**
     *
     * @param operationType Operation Type
     * @param returnType Return Type
     * @return The resolved {@link DataInterceptor} or {@literal null}.
     */
    @Nullable
    protected Class<? extends DataInterceptor> resolveInterceptorTypeByOperationType(MethodMatchInfo.OperationType operationType,
                                                                                     ClassElement returnType) {
        if (operationType == MethodMatchInfo.OperationType.DELETE) {
            if (TypeUtils.isReactiveType(returnType)) {
                return DeleteAllReactiveInterceptor.class;
            } else if (TypeUtils.isFutureType(returnType)) {
                return DeleteAllAsyncInterceptor.class;
            } else {
                return DeleteAllInterceptor.class;
            }
        }
        if (operationType == MethodMatchInfo.OperationType.UPDATE) {
            if (TypeUtils.isReactiveType(returnType)) {
                return UpdateReactiveInterceptor.class;
            } else if (TypeUtils.isFutureType(returnType)) {
                return UpdateAsyncInterceptor.class;
            } else {
                return UpdateInterceptor.class;
            }
        }
        return null;
    }

    /**
     *
     * @param matchContext The match context
     * @param returnType Return Type
     * @param queryResultType Query Result Type
     * @param query Query
     * @param isPage {@literal true} if the type argument performs the {@link  TypeRole#PAGE} role.
     * @param isSlice {@literal true} if the type argument performs the {@link  TypeRole#SLICE} role.
     * @return The resolved {@link DataInterceptor} or {@literal null}.
     */
    @NonNull
    protected Class<? extends DataInterceptor> resolveReactiveInterceptorType(@NonNull MethodMatchContext matchContext,
                                                                      @NonNull ClassElement returnType,
                                                                      @NonNull ClassElement queryResultType,
                                                                      @Nullable QueryModel query,
                                                                      boolean isPage,
                                                                      boolean isSlice) {
        if (isPage) {
            return FindPageReactiveInterceptor.class;
        } else if (isSlice) {
            return FindSliceReactiveInterceptor.class;
        } else {
            if (isReactiveSingleResult(returnType)) {
                if (isFindByIdQuery(matchContext, queryResultType, query)) {
                    return FindByIdReactiveInterceptor.class;
                } else {
                    return FindOneReactiveInterceptor.class;
                }
            } else {
                return FindAllReactiveInterceptor.class;
            }
        }
    }

    /**
     *
     * @param matchContext The match context
     * @param typeArgument Type Argument
     * @param queryResultType Query Result Type
     * @param query Query
     * @param isPage {@literal true} if the type argument performs the {@link  TypeRole#PAGE} role.
     * @param isSlice {@literal true} if the type argument performs the {@link  TypeRole#SLICE} role.
     * @return The resolved {@link DataInterceptor} or {@literal null}.
     */
    @Nullable
    protected Class<? extends DataInterceptor> resolveInterceptorType(@NonNull MethodMatchContext matchContext,
                                                                      @NonNull ClassElement typeArgument,
                                                                      @NonNull ClassElement queryResultType,
                                                                      @Nullable QueryModel query,
                                                                      boolean isPage,
                                                                      boolean isSlice) {
        if (isPage) {
            return FindPageAsyncInterceptor.class;
        } else if (isSlice) {
            return FindSliceAsyncInterceptor.class;
        } else if (typeArgument.isAssignable(Iterable.class)) {
            return FindAllAsyncInterceptor.class;
        } else if (isValidResultType(typeArgument)) {
            if (isFindByIdQuery(matchContext, queryResultType, query)) {
                return FindByIdAsyncInterceptor.class;
            } else {
                return FindOneAsyncInterceptor.class;
            }
        }
        return null;
    }

    /**
     *
     * @param matchContext The match context
     * @param typeArgument Type argument
     * @return {@literal true} if the type argument performs the {@link  TypeRole#PAGE} role.
     */
    protected boolean isPage(@NonNull MethodMatchContext matchContext, @NonNull ClassElement typeArgument) {
        return matchContext.isTypeInRole(
                typeArgument,
                TypeRole.PAGE
        );
    }

    /**
     *
     * @param matchContext The match context
     * @param typeArgument type argument
     * @return {@literal true} if the type argument performs the {@link  TypeRole#SLICE} role.
     */
    protected boolean isSlice(@NonNull MethodMatchContext matchContext, @NonNull ClassElement typeArgument) {
        return matchContext.isTypeInRole(
                typeArgument,
                TypeRole.SLICE
        );
    }

    /**
     * Obtain the interceptor element for the given class.
     *
     * @param matchContext The match context
     * @param type         The type
     * @return The element
     */
    protected ClassElement getInterceptorElement(@NonNull MethodMatchContext matchContext, Class<? extends DataInterceptor> type) {
        return matchContext.getVisitorContext().getClassElement(type).orElseGet(() -> new DynamicClassElement(type));
    }

    /**
     * Obtain the interceptor element for the given class name.
     *
     * @param matchContext The match context
     * @param type         The type
     * @return The element
     */
    protected ClassElement getInterceptorElement(@NonNull MethodMatchContext matchContext, String type) {
        return matchContext.getVisitorContext().getClassElement(type).orElseThrow(() -> new IllegalStateException("Unable to apply interceptor of type: " + type + ". The interceptor was not found on the classpath. Check your annotation processor configuration and try again."));
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
        if (!TypeUtils.areTypesCompatible(returnType, queryResultType)) {
            if (query != null && returnType.hasStereotype(Introspected.class) && queryResultType.hasStereotype(MappedEntity.class)) {
                if (ignoreAttemptProjection(query)) {
                    return true;
                }
                if (attemptProjection(matchContext, queryResultType, query, returnType)) {
                    return true;
                }
            } else {
                matchContext.fail("Query results in a type [" + queryResultType.getName() + "] whilst method returns an incompatible type: " + returnType.getName());
            }
        }
        return false;
    }

    private boolean isValidResultType(ClassElement returnType) {
        return returnType.hasStereotype(Introspected.class) || ClassUtils.isJavaBasicType(returnType.getName()) || returnType.isPrimitive();
    }

    private boolean isReactiveSingleResult(ClassElement returnType) {
        return returnType.hasStereotype(SingleResult.class) || returnType.isAssignable("io.reactivex.Single") || returnType.isAssignable("reactor.core.publisher.Mono");
    }

    /**
     *
     * @param matchContext Match Context
     * @param queryResultType Query Result Type
     * @param query Query
     * @param returnType Return Type
     * @return returns {@literal false} if the attempt to create the projection fails.
     */
    private boolean attemptProjection(@NonNull MethodMatchContext matchContext,
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
                matchContext.fail("Property " + propertyName + " is not present in entity: " + entity.getName());
                return false;
            }

            if (!TypeUtils.areTypesCompatible(beanProperty.getType(), pp.getType())) {
                matchContext.fail("Property [" + propertyName + "] of type [" + beanProperty.getType().getName() + "] is not compatible with equivalent property declared in entity: " + entity.getName());
                return false;
            }
            // add an alias projection for each property
            final QueryBuilder queryBuilder = matchContext.getQueryBuilder();
            if (queryBuilder.shouldAliasProjections()) {
                query.projections().add(Projections.property(propertyName).aliased());
            } else {
                query.projections().add(Projections.property(propertyName));
            }
        }
        return true;
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
     *
     * @param matchContext The match context
     * @return a List of annotations values for {@Join} annotation.
     */
    @NonNull
    protected List<AnnotationValue<Join>> joinSpecsAtMatchContext(@NonNull MethodMatchContext matchContext) {
        final MethodMatchInfo.OperationType operationType = getOperationType();
        if (operationType != MethodMatchInfo.OperationType.QUERY) {
            return matchContext.getAnnotationMetadata().getDeclaredAnnotationValuesByType(Join.class);
        }
        return matchContext.getAnnotationMetadata().getAnnotationValuesByType(Join.class);
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

    /**
     * Builds a raw query for the given match context. Should be called for methods annotated with {@link Query} explicitly.
     *
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
                String name = matcher.group(3);
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
                String name = matcher.group(3);
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

    /**
     * Internally used for dynamically defining a class element.
     */
    private static class DynamicClassElement implements ClassElement {
        private final Class<? extends DataInterceptor> type;

        DynamicClassElement(Class<? extends DataInterceptor> type) {
            this.type = type;
        }

        @Override
        public boolean isAssignable(String type) {
            return false;
        }

        @Override
        public ClassElement toArray() {
            return new DynamicClassElement((Class<? extends DataInterceptor>) Array.newInstance(type, 0).getClass());
        }

        @Override
        public ClassElement fromArray() {
            return new DynamicClassElement((Class<? extends DataInterceptor>) type.getComponentType());
        }

        @Nonnull
        @Override
        public String getName() {
            return type.getName();
        }

        @Override
        public boolean isProtected() {
            return Modifier.isProtected(type.getModifiers());
        }

        @Override
        public boolean isPublic() {
            return Modifier.isPublic(type.getModifiers());
        }

        @Nonnull
        @Override
        public Object getNativeType() {
            return type;
        }
    }
}
