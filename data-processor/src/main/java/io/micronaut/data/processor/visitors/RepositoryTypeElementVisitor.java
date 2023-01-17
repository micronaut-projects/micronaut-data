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
package io.micronaut.data.processor.visitors;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.Slice;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.model.criteria.impl.SourceParameterExpressionImpl;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.MethodMatcher;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The main {@link TypeElementVisitor} that visits interfaces annotated with {@link Repository}
 * and generates queries for each abstract method.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
public class RepositoryTypeElementVisitor implements TypeElementVisitor<Repository, Object> {

    public static final String SPRING_REPO = "org.springframework.data.repository.Repository";
    private static final boolean IS_DOCUMENT_ANNOTATION_PROCESSOR = ClassUtils.isPresent("io.micronaut.data.document.processor.mapper.MappedEntityMapper", RepositoryTypeElementVisitor.class.getClassLoader());

    private ClassElement currentClass;
    private ClassElement currentRepository;
    private QueryBuilder queryEncoder;
    private final Map<String, String> typeRoles = new HashMap<>();
    private final List<MethodMatcher> methodsMatchers;
    private boolean failing = false;
    private final Set<String> visitedRepositories = new HashSet<>();
    private Map<String, DataType> dataTypes = Collections.emptyMap();
    private final Map<String, SourcePersistentEntity> entityMap = new HashMap<>(50);
    private Function<ClassElement, SourcePersistentEntity> entityResolver;

    {
        List<MethodMatcher> matcherList = new ArrayList<>(20);
        SoftServiceLoader.load(MethodMatcher.class).collectAll(matcherList);
        OrderUtil.sort(matcherList);
        methodsMatchers = matcherList;
    }

    /**
     * Default constructor.
     */
    public RepositoryTypeElementVisitor() {
        typeRoles.put(Pageable.class.getName(), TypeRole.PAGEABLE);
        typeRoles.put(Sort.class.getName(), TypeRole.SORT);
        typeRoles.put(Page.class.getName(), TypeRole.PAGE);
        typeRoles.put(Slice.class.getName(), TypeRole.SLICE);
    }

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        String interfaceName = element.getName();
        if (failing) {
            return;
        }
        if (visitedRepositories.contains(interfaceName)) {
            // prevent duplicate visits
            currentRepository = null;
            currentClass = null;
            return;
        }
        if (element.hasStereotype("io.micronaut.data.document.annotation.DocumentProcessorRequired") && !IS_DOCUMENT_ANNOTATION_PROCESSOR) {
            context.fail("Repository is required to be processed by the data-document-processor. " +
                "Make sure it's included as a dependency to the annotation processor classpath!", element);
            failing = true;
            return;
        }

        this.currentClass = element;

        entityResolver = new Function<ClassElement, SourcePersistentEntity>() {

            final MappedEntityVisitor mappedEntityVisitor = new MappedEntityVisitor();
            final MappedEntityVisitor embeddedMappedEntityVisitor = new MappedEntityVisitor(false);

            @Override
            public SourcePersistentEntity apply(ClassElement classElement) {
                return entityMap.computeIfAbsent(classElement.getName(), s -> {
                    if (classElement.hasAnnotation("io.micronaut.data.annotation.Embeddable")) {
                        embeddedMappedEntityVisitor.visitClass(classElement, context);
                    } else {
                        mappedEntityVisitor.visitClass(classElement, context);
                    }
                    return new SourcePersistentEntity(classElement, this);
                });
            }
        };

        if (element.hasDeclaredStereotype(Repository.class)) {
            visitedRepositories.add(interfaceName);
            currentRepository = element;
            queryEncoder = QueryBuilder.newQueryBuilder(element.getAnnotationMetadata());
            this.dataTypes = Utils.getConfiguredDataTypes(currentRepository);
            AnnotationMetadata annotationMetadata = element.getAnnotationMetadata();
            List<AnnotationValue<TypeRole>> roleArray = annotationMetadata
                    .findAnnotation(RepositoryConfiguration.class)
                    .map(av -> av.getAnnotations("typeRoles", TypeRole.class))
                    .orElse(Collections.emptyList());
            for (AnnotationValue<TypeRole> parameterRole : roleArray) {
                String role = parameterRole.stringValue("role").orElse(null);
                AnnotationClassValue cv = parameterRole.get("type", AnnotationClassValue.class).orElse(null);
                if (StringUtils.isNotEmpty(role) && cv != null) {
                    context.getClassElement(cv.getName()).ifPresent(ce ->
                            typeRoles.put(ce.getName(), role)
                    );
                }
            }
            if (element.isAssignable(SPRING_REPO)) {
                context.getClassElement("org.springframework.data.domain.Pageable").ifPresent(ce ->
                        typeRoles.put(ce.getName(), TypeRole.PAGEABLE)
                );
                context.getClassElement("org.springframework.data.domain.Page").ifPresent(ce ->
                        typeRoles.put(ce.getName(), TypeRole.PAGE)
                );
                context.getClassElement("org.springframework.data.domain.Slice").ifPresent(ce ->
                        typeRoles.put(ce.getName(), TypeRole.SLICE)
                );
                context.getClassElement("org.springframework.data.domain.Sort").ifPresent(ce ->
                        typeRoles.put(ce.getName(), TypeRole.SORT)
                );
            }
            if (queryEncoder == null) {
                context.fail("QueryEncoder not present on annotation processor path", element);
                failing = true;
            }
        }

    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (currentRepository == null || failing) {
            return;
        }
        ClassElement genericReturnType = element.getGenericReturnType();
        if (queryEncoder != null && currentClass != null && element.isAbstract() && !element.isStatic() && methodsMatchers != null) {
            ParameterElement[] parameters = element.getParameters();
            Map<String, Element> parametersInRole = new HashMap<>(2);
            for (ParameterElement parameter : parameters) {
                ClassElement type = parameter.getType();
                this.typeRoles.entrySet().stream().filter(entry -> {
                            String roleType = entry.getKey();
                            return type.isAssignable(roleType);
                        }
                ).forEach(entry ->
                        parametersInRole.put(entry.getValue(), parameter)
                );
            }

            if (element.hasDeclaredAnnotation(DataMethod.class)) {
                // explicitly handled
                return;
            }

            MatchContext matchContext = new MatchContext(
                    queryEncoder,
                    currentRepository,
                    context,
                    element,
                    typeRoles,
                    genericReturnType,
                    parameters
            );

            try {
                SourcePersistentEntity entity = resolvePersistentEntity(element, parametersInRole);
                MethodMatchContext methodMatchContext = new MethodMatchContext(
                        queryEncoder,
                        currentRepository,
                        entity,
                        context,
                        genericReturnType,
                        element,
                        parametersInRole,
                        typeRoles,
                        parameters,
                        entityResolver
                );

                for (MethodMatcher finder : methodsMatchers) {
                    MethodMatcher.MethodMatch matcher = finder.match(methodMatchContext);
                    if (matcher == null) {
                        continue;
                    }

                    MethodMatchInfo methodInfo = matcher.buildMatchInfo(methodMatchContext);
                    if (methodInfo == null) {
                        continue;
                    }

                    processMethodInfo(methodMatchContext, methodInfo);
                    return;
                }
                if (matchContext.isPossiblyFailing()) {
                    matchContext.logPossibleFailures();
                } else {
                    String messageStart = matchContext.getUnableToImplementMessage();
                    context.fail(messageStart + "No possible implementations found.", element);
                }
                this.failing = true;
            } catch (MatchFailedException e) {
                context.fail(matchContext.getUnableToImplementMessage() + e.getMessage(), e.getElement() == null ? element : e.getElement());
                this.failing = true;
            } catch (Exception e) {
                matchContext.fail(e.getMessage());
                this.failing = true;
            }
        }
    }

    private void processMethodInfo(MethodMatchContext methodMatchContext, MethodMatchInfo methodInfo) {
        QueryBuilder queryEncoder = methodMatchContext.getQueryBuilder();
        MethodElement element = methodMatchContext.getMethodElement();
        SourcePersistentEntity entity = methodMatchContext.getRootEntity();
        ParameterElement[] parameters = methodMatchContext.getParameters();

        // populate parameter roles
        for (Map.Entry<String, Element> entry : methodMatchContext.getParametersInRole().entrySet()) {
            methodInfo.addParameterRole(
                    entry.getKey(),
                    entry.getValue().getName()
            );
        }

        List<QueryParameterBinding> parameterBinding = null;
        boolean encodeEntityParameters = false;
        boolean supportsImplicitQueries = methodMatchContext.supportsImplicitQueries();
        QueryResult queryResult = methodInfo.getQueryResult();
        if (queryResult != null) {
            if (methodInfo.isRawQuery()) {
                // no need to annotation since already annotated, just replace the
                // the computed parameter names
                parameterBinding = queryResult.getParameterBindings();

                element.annotate(Query.class, (builder) -> builder.member(DataMethod.META_MEMBER_RAW_QUERY,
                        element.stringValue(Query.class)
                                .map(q -> addRawQueryParameterPlaceholders(queryEncoder, queryResult.getQuery(), queryResult.getQueryParts()))
                                .orElse(null)));

                ClassElement genericReturnType = methodMatchContext.getReturnType();
                if (methodMatchContext.isTypeInRole(genericReturnType, TypeRole.PAGE) || element.isPresent(Query.class, "countQuery")) {
                    QueryResult countQueryResult = methodInfo.getCountQueryResult();
                    if (countQueryResult == null) {
                        throw new MatchFailedException("Query returns a Page and does not specify a 'countQuery' member.", element);
                    } else {
                        element.annotate(
                                Query.class,
                                (builder) -> builder.member(DataMethod.META_MEMBER_RAW_COUNT_QUERY, addRawQueryParameterPlaceholders(queryEncoder, countQueryResult.getQuery(), countQueryResult.getQueryParts()))
                        );
                    }
                }

                encodeEntityParameters = methodInfo.isEncodeEntityParameters();
            } else {

                encodeEntityParameters = methodInfo.isEncodeEntityParameters();
                parameterBinding = queryResult.getParameterBindings();
                bindAdditionalParameters(methodMatchContext, entity, parameterBinding, parameters, queryResult.getAdditionalRequiredParameters());

                QueryResult preparedCount = methodInfo.getCountQueryResult();
                if (preparedCount != null) {
                    element.annotate(Query.class, annotationBuilder -> {
                                annotationBuilder.value(queryResult.getQuery());
                                annotationBuilder.member(DataMethod.META_MEMBER_COUNT_QUERY, preparedCount.getQuery());
                            }
                    );
                } else {
                    element.annotate(Query.class, annotationBuilder -> {
                                annotationBuilder.value(queryResult.getQuery());
                                String update = queryResult.getUpdate();
                                if (StringUtils.isNotEmpty(update)) {
                                    annotationBuilder.member("update", update);
                                }
                            }
                    );
                }

                Collection<JoinPath> joinPaths = queryResult.getJoinPaths();
                if (CollectionUtils.isNotEmpty(joinPaths)) {
                    // Only apply the changes if joins aren't empty.
                    // Implementation might choose to return an empty array to skip the modification of existing annotations.
                    element.removeAnnotation(Join.class);
                    joinPaths.forEach(joinPath -> element.annotate(Join.class, builder -> {
                        builder.member("value", joinPath.getPath())
                            .member("type", joinPath.getJoinType());
                        if (joinPath.getAlias().isPresent()) {
                            builder.member("alias", joinPath.getAlias().get());
                        }
                    }));
                }
            }
        }

        ClassElement runtimeInterceptor = methodInfo.getRuntimeInterceptor();
        if (runtimeInterceptor == null) {
            throw new MatchFailedException("Unable to implement Repository method: " + currentRepository.getSimpleName() + "." + element.getName() + "(..). No possible runtime implementations found.", element);
        }

        boolean finalEncodeEntityParameters = encodeEntityParameters;
        List<QueryParameterBinding> finalParameterBinding = parameterBinding;
        element.annotate(DataMethod.class, annotationBuilder -> {

            annotationBuilder.member(DataMethod.META_MEMBER_OPERATION_TYPE, methodInfo.getOperationType());
            annotationBuilder.member(DataMethod.META_MEMBER_ROOT_ENTITY, new AnnotationClassValue<>(entity.getName()));

            // include the roles
            methodInfo.getParameterRoles().forEach(annotationBuilder::member);

            if (methodInfo.isDto()) {
                annotationBuilder.member(DataMethod.META_MEMBER_DTO, true);
            }
            if (methodInfo.isOptimisticLock()) {
                annotationBuilder.member(DataMethod.META_MEMBER_OPTIMISTIC_LOCK, true);
            }

            TypedElement resultType = methodInfo.getResultType();
            if (resultType != null) {
                annotationBuilder.member(DataMethod.META_MEMBER_RESULT_TYPE, new AnnotationClassValue<>(resultType.getName()));
                ClassElement type = resultType.getType();
                if (!TypeUtils.isVoid(type)) {
                    annotationBuilder.member(DataMethod.META_MEMBER_RESULT_DATA_TYPE, TypeUtils.resolveDataType(type, dataTypes));
                }
            }
            String idType = resolveIdType(entity);
            if (idType != null) {
                annotationBuilder.member(DataMethod.META_MEMBER_ID_TYPE, idType);
            }
            annotationBuilder.member(DataMethod.META_MEMBER_INTERCEPTOR, new AnnotationClassValue<>(runtimeInterceptor.getName()));

            if (queryResult != null) {
                if (finalParameterBinding.stream().anyMatch(QueryParameterBinding::isExpandable)) {
                    annotationBuilder.member(DataMethod.META_MEMBER_EXPANDABLE_QUERY, queryResult.getQueryParts().toArray(new String[0]));
                    QueryResult preparedCount = methodInfo.getCountQueryResult();
                    if (preparedCount != null) {
                        annotationBuilder.member(DataMethod.META_MEMBER_EXPANDABLE_COUNT_QUERY, preparedCount.getQueryParts().toArray(new String[0]));
                    }
                }

                int max = queryResult.getMax();
                if (max > -1) {
                    annotationBuilder.member(DataMethod.META_MEMBER_PAGE_SIZE, max);
                }
                long offset = queryResult.getOffset();
                if (offset > 0) {
                    annotationBuilder.member(DataMethod.META_MEMBER_PAGE_INDEX, offset);
                }
            }

            Arrays.stream(parameters)
                    .filter(p -> p.getGenericType().isAssignable(entity.getName()))
                    .findFirst()
                    .ifPresent(parameterElement -> annotationBuilder.member(DataMethod.META_MEMBER_ENTITY, parameterElement.getName()));

            if (CollectionUtils.isNotEmpty(finalParameterBinding)) {
                bindParameters(supportsImplicitQueries, finalParameterBinding, finalEncodeEntityParameters, annotationBuilder);
            }

        });
    }

    private void bindParameters(boolean supportsImplicitQueries,
                                List<QueryParameterBinding> finalParameterBinding,
                                boolean finalEncodeEntityParameters,
                                AnnotationValueBuilder<DataMethod> annotationBuilder) {

        List<AnnotationValue<?>> annotationValues = new ArrayList<>();
        for (QueryParameterBinding p : finalParameterBinding) {
            AnnotationValueBuilder<?> builder = AnnotationValue.builder(DataMethodQueryParameter.class);
            if (p.getParameterIndex() != -1) {
                builder.member(DataMethodQueryParameter.META_MEMBER_PARAMETER_INDEX, p.getParameterIndex());
            }
            if (p.getParameterBindingPath() != null) {
                builder.member(DataMethodQueryParameter.META_MEMBER_PARAMETER_BINDING_PATH, p.getParameterBindingPath());
            }
            if (p.getPropertyPath() != null) {
                if (p.getPropertyPath().length == 1) {
                    builder.member(DataMethodQueryParameter.META_MEMBER_PROPERTY, p.getPropertyPath()[0]);
                } else {
                    builder.member(DataMethodQueryParameter.META_MEMBER_PROPERTY_PATH, p.getPropertyPath());
                }
            }
            if (!supportsImplicitQueries && !finalEncodeEntityParameters) {
                builder.member(DataMethodQueryParameter.META_MEMBER_DATA_TYPE, p.getDataType());
            }
            if (p.getConverterClassName() != null) {
                builder.member(DataMethodQueryParameter.META_MEMBER_CONVERTER, new AnnotationClassValue<>(p.getConverterClassName()));
            }
            if (p.isAutoPopulated()) {
                builder.member(DataMethodQueryParameter.META_MEMBER_AUTO_POPULATED, true);
            }
            if (p.isRequiresPreviousPopulatedValue()) {
                builder.member(DataMethodQueryParameter.META_MEMBER_REQUIRES_PREVIOUS_POPULATED_VALUES, true);
            }
            if (p.isExpandable()) {
                builder.member(DataMethodQueryParameter.META_MEMBER_EXPANDABLE, true);
            }
            if (supportsImplicitQueries) {
                builder.member(DataMethodQueryParameter.META_MEMBER_NAME, p.getKey());
            }
            annotationValues.add(builder.build());
        }
        AnnotationValue[] annotations = annotationValues.toArray(new AnnotationValue[0]);
        annotationBuilder.member(DataMethod.META_MEMBER_PARAMETERS, annotations);
    }

    private void bindAdditionalParameters(MatchContext matchContext,
                                          SourcePersistentEntity entity,
                                          List<QueryParameterBinding> parameterBinding,
                                          ParameterElement[] parameters,
                                          Map<String, String> params) {
        if (CollectionUtils.isNotEmpty(params)) {
            Map<String, DataType> configuredDataTypes = Utils.getConfiguredDataTypes(matchContext.getRepositoryClass());
            for (Map.Entry<String, String> param : params.entrySet()) {
                ParameterElement parameter = Arrays.stream(parameters)
                        .filter(p -> p.stringValue(Parameter.class).orElse(p.getName()).equals(param.getValue()))
                        .findFirst().orElse(null);
                if (parameter == null) {
                    throw new MatchFailedException("A @Where(..) definition requires a parameter called [" + param.getValue() + "] which is not present in the method signature.");
                }
                PersistentPropertyPath propertyPath = entity.getPropertyPath(parameter.getName());
                BindingParameter.BindingContext bindingContext = BindingParameter.BindingContext.create()
                        .name(param.getKey())
                        .incomingMethodParameterProperty(propertyPath)
                        .outgoingQueryParameterProperty(propertyPath);
                QueryParameterBinding binding = new SourceParameterExpressionImpl(configuredDataTypes,
                        matchContext.parameters,
                        parameter,
                        false).bind(bindingContext);
                parameterBinding.add(binding);
            }
        }
    }

    private String addRawQueryParameterPlaceholders(QueryBuilder queryEncoder, String query, List<String> queryParts) {
        if (queryEncoder instanceof SqlQueryBuilder) {
            Iterator<String> iterator = queryParts.iterator();
            String first = iterator.next();
            if (queryParts.size() < 2) {
                return first;
            }
            StringBuilder sb = new StringBuilder(first);
            int i = 1;
            while (iterator.hasNext()) {
                sb.append(((SqlQueryBuilder) queryEncoder).formatParameter(i++).getName());
                sb.append(iterator.next());
            }
            return sb.toString();
        }
        return query;
    }

    @Nullable
    private String resolveIdType(PersistentEntity entity) {
        Map<String, ClassElement> typeArguments = currentRepository.getTypeArguments(GenericRepository.class);
        String varName = "ID";
        if (typeArguments.isEmpty()) {
            typeArguments = currentRepository.getTypeArguments(RepositoryTypeElementVisitor.SPRING_REPO);
        }
        if (!typeArguments.isEmpty()) {
            ClassElement ce = typeArguments.get(varName);
            if (ce != null) {
                return ce.getName();
            }
        }
        PersistentProperty identity = entity.getIdentity();
        if (identity != null) {
            return identity.getName();
        }
        return null;
    }

    private SourcePersistentEntity resolvePersistentEntity(MethodElement element, Map<String, Element> parametersInRole) {
        ClassElement returnType = element.getGenericReturnType();
        SourcePersistentEntity entity = resolveEntityForCurrentClass();
        if (entity == null) {
            entity = Utils.resolvePersistentEntity(returnType, entityResolver);
        }

        if (entity != null) {
            List<PersistentProperty> propertiesInRole = entity.getPersistentProperties()
                    .stream().filter(pp -> pp.getAnnotationMetadata().hasStereotype(TypeRole.class))
                    .collect(Collectors.toList());
            for (PersistentProperty persistentProperty : propertiesInRole) {
                String role = persistentProperty.getAnnotationMetadata().getValue(TypeRole.class, "role", String.class).orElse(null);
                if (role != null) {
                    parametersInRole.put(role, ((SourcePersistentProperty) persistentProperty).getPropertyElement());
                }
            }
            return entity;
        } else {
            throw new MatchFailedException("Could not resolved root entity. Either implement the Repository interface or define the entity as part of the signature", element);
        }
    }

    @Nullable
    private SourcePersistentEntity resolveEntityForCurrentClass() {
        Map<String, ClassElement> typeArguments = currentRepository.getTypeArguments(GenericRepository.class);
        String argName = "E";
        if (typeArguments.isEmpty()) {
            argName = "T";
            typeArguments = currentRepository.getTypeArguments(SPRING_REPO);
        }
        if (!typeArguments.isEmpty()) {
            ClassElement ce = typeArguments.get(argName);
            if (ce != null) {
                return entityResolver.apply(ce);
            }
        }
        return null;
    }

}
