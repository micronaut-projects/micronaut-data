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
package io.micronaut.data.processor.visitors;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.*;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.*;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.*;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.finders.*;
import io.micronaut.data.processor.visitors.finders.page.FindPageByMethod;
import io.micronaut.data.processor.visitors.finders.page.ListPageMethod;
import io.micronaut.data.processor.visitors.finders.slice.FindSliceByMethod;
import io.micronaut.data.processor.visitors.finders.slice.ListSliceMethod;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.inject.ast.*;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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

    private ClassElement currentClass;
    private ClassElement currentRepository;
    private QueryBuilder queryEncoder;
    private Map<String, String> typeRoles = new HashMap<>();
    private List<MethodCandidate> finders;
    private boolean failing = false;
    private Set<String> visitedRepositories = new HashSet<>();
    private Map<String, DataType> dataTypes = Collections.emptyMap();
    private Map<String, SourcePersistentEntity> entityMap = new HashMap<>(50);
    private final Function<ClassElement, SourcePersistentEntity> entityResolver = new Function<ClassElement, SourcePersistentEntity>() {
        @Override
        public SourcePersistentEntity apply(ClassElement classElement) {
            return entityMap.computeIfAbsent(classElement.getName(), s -> new SourcePersistentEntity(classElement, this));
        }
    };

    /**
     * Default constructor.
     */
    public RepositoryTypeElementVisitor() {
        typeRoles.put(Pageable.class.getName(), TypeRole.PAGEABLE);
        typeRoles.put(Sort.class.getName(), TypeRole.SORT);
        typeRoles.put(Page.class.getName(), TypeRole.PAGE);
        typeRoles.put(Slice.class.getName(), TypeRole.SLICE);
    }

    @Override
    public void start(VisitorContext visitorContext) {
        if (finders == null) {
            finders = initializeMethodCandidates(visitorContext);
        }
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
        this.currentClass = element;

        if (element.hasDeclaredStereotype(Repository.class)) {
            visitedRepositories.add(interfaceName);
            currentRepository = element;
            queryEncoder = QueryBuilder.newQueryBuilder(element.getAnnotationMetadata());
            this.dataTypes = MappedEntityVisitor.getConfiguredDataTypes(currentRepository);
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
        if (queryEncoder != null && currentClass != null && element.isAbstract() && !element.isStatic() && finders != null) {
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
                    currentRepository,
                    context,
                    element,
                    typeRoles,
                    genericReturnType,
                    parameters
            );
            for (MethodCandidate finder : finders) {
                if (finder.isMethodMatch(element, matchContext)) {
                    SourcePersistentEntity entity = resolvePersistentEntity(element, parametersInRole, context);

                    if (entity == null) {
                        matchContext.fail("Unable to establish persistent entity to query for method: " + element.getName());
                        this.failing = matchContext.isFailing();
                        return;
                    }

                    String idType = resolveIdType(entity);



                    MethodMatchContext methodMatchContext = new MethodMatchContext(
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
                    MethodMatchInfo methodInfo;
                    try {
                        methodInfo = finder.buildMatchInfo(methodMatchContext);
                    } catch (Exception e) {
                        matchContext.fail(e.getMessage());
                        return;
                    }
                    if (methodInfo != null) {

                        // populate parameter roles
                        for (Map.Entry<String, Element> entry : methodMatchContext.getParametersInRole().entrySet()) {
                            methodInfo.addParameterRole(
                                    entry.getKey(),
                                    entry.getValue().getName()
                            );
                        }

                        QueryModel queryObject = methodInfo.getQuery();
                        QueryModel countQuery;
                        QueryResult encodedQuery;
                        QueryResult preparedCount = null;
                        Map<String, String> parameterBinding = null;
                        Map<String, DataType> parameterTypes = Collections.emptyMap();
                        boolean rawCount = false;
                        if (queryObject != null) {
                            if (queryObject instanceof RawQuery) {
                                RawQuery rawQuery = (RawQuery) queryObject;

                                // no need to annotation since already annotated, just replace the
                                // the computed parameter names
                                parameterBinding = rawQuery.getParameterBinding();
                                if (matchContext.isTypeInRole(genericReturnType, TypeRole.PAGE)) {
                                    String cq = matchContext.getAnnotationMetadata().stringValue(io.micronaut.data.annotation.Query.class, "countQuery")
                                            .orElse(null);

                                    if (StringUtils.isEmpty(cq)) {
                                        methodMatchContext.fail("Query returns a Page and does not specify a 'countQuery' member.");
                                        this.failing = true;
                                        return;
                                    } else {
                                        rawCount = true;
                                    }
                                }
                            } else {

                                final AnnotationMetadataHierarchy annotationMetadataHierarchy = new AnnotationMetadataHierarchy(
                                        currentRepository.getAnnotationMetadata(),
                                        matchContext.getAnnotationMetadata()
                                );
                                try {
                                    switch (methodInfo.getOperationType()) {
                                        case DELETE:
                                            encodedQuery = queryEncoder.buildDelete(annotationMetadataHierarchy, queryObject);
                                            break;
                                        case UPDATE:
                                            encodedQuery = queryEncoder
                                                    .buildUpdate(
                                                            annotationMetadataHierarchy,
                                                            queryObject,
                                                            methodInfo.getUpdateProperties());
                                            break;
                                        case INSERT:
                                            // TODO
                                        default:

                                            encodedQuery = queryEncoder.buildQuery(
                                                    annotationMetadataHierarchy,
                                                    queryObject
                                            );
                                    }

                                } catch (Exception e) {
                                    methodMatchContext.fail("Invalid query method [" + element.getName() + "] of repository [" + currentRepository.getName() + "]: " + e.getMessage());
                                    this.failing = true;
                                    return;
                                }

                                parameterBinding = encodedQuery.getParameters();
                                parameterTypes = encodedQuery.getParameterTypes();
                                final Set<String> requiredParams = encodedQuery.getAdditionalRequiredParameters();
                                if (CollectionUtils.isNotEmpty(requiredParams)) {
                                    for (String requiredParam : requiredParams) {
                                        final boolean hasNoParameter = Arrays.stream(parameters)
                                                .noneMatch(p ->
                                                        p.stringValue(Parameter.class)
                                                                .orElse(p.getName())
                                                                .equals(requiredParam)
                                                );
                                        if (hasNoParameter) {
                                            methodMatchContext.fail("A @Where(..) definition requires a parameter called [" + requiredParam + "] which is not present in the method signature.");
                                        }
                                    }
                                }

                                if (TypeUtils.isReactiveOrFuture(genericReturnType)) {
                                    genericReturnType = genericReturnType.getFirstTypeArgument().orElse(entity.getType());
                                }
                                if (matchContext.isTypeInRole(genericReturnType, TypeRole.PAGE)) {
                                    countQuery = QueryModel.from(queryObject.getPersistentEntity());
                                    countQuery.projections().count();
                                    QueryModel.Junction junction = queryObject.getCriteria();
                                    for (QueryModel.Criterion criterion : junction.getCriteria()) {
                                        countQuery.add(criterion);
                                    }

                                    preparedCount = queryEncoder.buildQuery(countQuery);

                                    QueryResult finalPreparedCount = preparedCount;
                                    element.annotate(io.micronaut.data.annotation.Query.class, annotationBuilder -> {
                                                annotationBuilder.value(encodedQuery.getQuery());
                                                annotationBuilder.member(DataMethod.META_MEMBER_COUNT_QUERY, finalPreparedCount.getQuery());
                                            }
                                    );
                                } else {
                                    element.annotate(io.micronaut.data.annotation.Query.class, annotationBuilder ->
                                            annotationBuilder.value(encodedQuery.getQuery())
                                    );
                                }
                            }
                        }

                        Class<? extends DataInterceptor> runtimeInterceptor = methodInfo.getRuntimeInterceptor();

                        if (runtimeInterceptor != null) {
                            Map<String, String> finalParameterBinding = parameterBinding;
                            QueryResult finalPreparedCount1 = preparedCount;
                            boolean finalRawCount = rawCount;
                            Map<String, DataType> finalParameterTypes = parameterTypes;
                            element.annotate(DataMethod.class, annotationBuilder -> {

                                if (runtimeInterceptor.getSimpleName().startsWith("Save")) {
                                    try {
                                        QueryResult queryResult = queryEncoder.buildInsert(currentRepository.getAnnotationMetadata(), entity);
                                        if (queryResult != null) {
                                            Map<String, String> qp = queryResult.getParameters();
                                            addParameterTypeDefinitions(methodMatchContext, qp, parameters, annotationBuilder, queryResult.getParameterTypes());

                                            AnnotationValue<?>[] annotationValues = parameterBindingToAnnotationValues(qp);
                                            annotationBuilder.member(DataMethod.META_MEMBER_INSERT_STMT, queryResult.getQuery());
                                            annotationBuilder.member(DataMethod.META_MEMBER_INSERT_BINDING, annotationValues);
                                        }
                                    } catch (Exception e) {
                                        context.fail("Error building insert statement", element);
                                        failing = true;
                                    }
                                }
                                annotationBuilder.member(DataMethod.META_MEMBER_ROOT_ENTITY, new AnnotationClassValue<>(entity.getName()));

                                // include the roles
                                methodInfo.getParameterRoles()
                                        .forEach(annotationBuilder::member);

                                if (methodInfo.isDto()) {
                                    annotationBuilder.member(DataMethod.META_MEMBER_DTO, true);
                                }

                                TypedElement resultType = methodInfo.getResultType();
                                if (resultType != null) {
                                    annotationBuilder.member(DataMethod.META_MEMBER_RESULT_TYPE, new AnnotationClassValue<>(resultType.getName()));

                                    ClassElement type = resultType.getType();
                                    if (!type.getName().equals("void")) {
                                        annotationBuilder.member(DataMethod.META_MEMBER_RESULT_DATA_TYPE, TypeUtils.resolveDataType(type, dataTypes));
                                    }
                                }
                                if (idType != null) {
                                    annotationBuilder.member(DataMethod.META_MEMBER_ID_TYPE, idType);
                                }
                                annotationBuilder.member(DataMethod.META_MEMBER_INTERCEPTOR, runtimeInterceptor);
                                boolean supportsImplicitQueries = matchContext.supportsImplicitQueries();
                                if (CollectionUtils.isNotEmpty(finalParameterBinding)) {
                                    if (!supportsImplicitQueries) {
                                        addParameterTypeDefinitions(
                                                methodMatchContext,
                                                finalParameterBinding,
                                                parameters,
                                                annotationBuilder,
                                                finalParameterTypes
                                        );
                                    }

                                    if (finalRawCount) {
                                        parameterBindingToIndex(
                                                annotationBuilder,
                                                parameters,
                                                finalParameterBinding,
                                                methodMatchContext,
                                                supportsImplicitQueries,
                                                DataMethod.META_MEMBER_COUNT_PARAMETERS,
                                                DataMethod.META_MEMBER_PARAMETER_BINDING);
                                    } else {
                                        parameterBindingToIndex(
                                                annotationBuilder,
                                                parameters,
                                                finalParameterBinding,
                                                methodMatchContext,
                                                supportsImplicitQueries,
                                                DataMethod.META_MEMBER_PARAMETER_BINDING
                                        );
                                    }
                                }
                                if (finalPreparedCount1 != null) {
                                    parameterBindingToIndex(
                                            annotationBuilder,
                                            parameters,
                                            finalPreparedCount1.getParameters(),
                                            methodMatchContext,
                                            supportsImplicitQueries,
                                            DataMethod.META_MEMBER_COUNT_PARAMETERS
                                    );
                                }

                                Optional<ParameterElement> entityParam = Arrays.stream(parameters).filter(p -> {
                                    ClassElement t = p.getGenericType();
                                    return t.isAssignable(entity.getName());
                                }).findFirst();
                                entityParam.ifPresent(parameterElement -> annotationBuilder.member(DataMethod.META_MEMBER_ENTITY, parameterElement.getName()));

                                for (Map.Entry<String, String> entry : methodInfo.getParameterRoles().entrySet()) {
                                    annotationBuilder.member(entry.getKey(), entry.getValue());
                                }
                                if (queryObject != null) {
                                    if (queryObject instanceof RawQuery) {
                                        element.annotate(Query.class, (builder) -> builder.member(DataMethod.META_MEMBER_RAW_QUERY, true));
                                    }
                                    int max = queryObject.getMax();
                                    if (max > -1) {
                                        annotationBuilder.member(DataMethod.META_MEMBER_PAGE_SIZE, max);
                                    }
                                    long offset = queryObject.getOffset();
                                    if (offset > 0) {
                                        annotationBuilder.member(DataMethod.META_MEMBER_PAGE_INDEX, offset);
                                    }
                                }
                            });
                            return;
                        } else {
                            methodMatchContext.fail("Unable to implement Repository method: " + currentRepository.getSimpleName() + "." + element.getName() + "(..). No possible runtime implementations found.");
                            this.failing = true;
                            return;
                        }
                    }

                    this.failing = methodMatchContext.isFailing();

                }
            }

            this.failing = true;

            String messageStart = matchContext.getUnableToImplementMessage();
            context.fail(messageStart + "No possible implementations found.", element);
        }
    }

    private void parameterBindingToIndex(
            AnnotationValueBuilder<DataMethod> annotationBuilder,
            ParameterElement[] parameters,
            Map<String, String> finalParameterBinding,
            MethodMatchContext methodMatchContext,
            boolean includeNames,
            String... members) {
        List<String> parameterNames = Arrays.stream(parameters).map(parameterElement ->
                parameterElement.stringValue(Parameter.class).orElse(parameterElement.getName())).collect(Collectors.toList()
        );
        int len = finalParameterBinding.size();
        String[] parameterPaths = new String[len];
        String[] nameIndex = new String[len];
        AtomicInteger ai = new AtomicInteger(0);
        int[] parameterIndices = finalParameterBinding.entrySet().stream().map(entry -> {
            String parameterName = entry.getValue();
            int pathIndex = ai.getAndIncrement();
            parameterPaths[pathIndex] = "";
            nameIndex[pathIndex] = entry.getKey();
            int i = parameterNames.indexOf(parameterName);
            if (i > -1) {
                return i;
            } else {
                int j = parameterName.indexOf('.');
                if (j > -1) {
                    String prop = parameterName.substring(0, j);
                    int paramIndex = parameterNames.indexOf(prop);
                    parameterPaths[pathIndex] = paramIndex + "." + parameterName.substring(j + 1);
                    return -1;
                } else {
                    // -1 indicates special handling for parameters in roles etc.
                    Map<String, Element> parametersInRole = methodMatchContext.getParametersInRole();
                    for (Map.Entry<String, Element> roleEntry : parametersInRole.entrySet()) {
                        Element element = roleEntry.getValue();
                        if (element instanceof PropertyElement) {
                            String name = element.getName();
                            if (name.equals(parameterName)) {
                                parameterPaths[pathIndex] = name;
                                break;
                            }
                        }
                    }
                    return -1;
                }

            }
        }).mapToInt(i -> i).toArray();
        for (String member : members) {
            annotationBuilder.member(member, parameterIndices);
            annotationBuilder.member(member + "Paths", parameterPaths);
            if (includeNames) {
                annotationBuilder.member(member + "Names", nameIndex);
            }
        }


    }

    private void addParameterTypeDefinitions(
            MethodMatchContext matchContext,
            Map<String, String> parameterBinding,
            ParameterElement[] parameters,
            AnnotationValueBuilder<DataMethod> annotationBuilder,
            Map<String, DataType> parameterTypes) {
        if (!matchContext.supportsImplicitQueries()) {
            Map<String, ParameterElement> paramMap = Arrays.stream(parameters).collect(Collectors.toMap(Element::getName, p -> p));
            DataType[] parameterDataTypes = new DataType[parameterBinding.size()];
            int i = 0;
            for (String value : parameterBinding.values()) {
                ParameterElement parameter = paramMap.get(value);
                if (parameter != null) {
                    DataType dt = parameterTypes.get(parameter.getName());
                    ClassElement genericType = parameter.getGenericType();
                    if (TypeUtils.isEntityContainerType(genericType) || genericType.hasStereotype(MappedEntity.class)) {
                        dt = DataType.ENTITY;
                    }
                    if (dt == null) {
                        if (TypeUtils.isContainerType(genericType)) {
                            genericType = genericType.getFirstTypeArgument().orElse(genericType);
                        }
                        ClassElement finalGenericType = genericType;
                        dt = parameter.enumValue(TypeDef.class, "type", DataType.class)
                                .orElseGet(() -> TypeUtils.resolveDataType(finalGenericType, dataTypes));
                    }


                    parameterDataTypes[i++] = dt;
                } else {
                    int dot = value.indexOf('.');
                    if (dot > -1) {
                        String val = value.substring(0, dot);
                        ParameterElement parameterElement = paramMap.get(val);
                        if (parameterElement != null) {

                            SourcePersistentEntity sourcePersistentEntity = resolvePersistentEntity(parameterElement.getGenericType());
                            if (sourcePersistentEntity != null) {
                                String name = value.substring(dot + 1);
                                SourcePersistentProperty subProp = findProp(sourcePersistentEntity, name);
                                if (subProp != null) {
                                    parameterDataTypes[i++] = subProp.getDataType();
                                } else {
                                    parameterDataTypes[i++] = DataType.OBJECT;
                                }
                            } else {
                                parameterDataTypes[i++] = DataType.OBJECT;
                            }
                        } else {
                            parameterDataTypes[i++] = DataType.OBJECT;
                        }
                    } else {
                        SourcePersistentProperty prop = findProp(matchContext, value);
                        if (prop != null) {
                            parameterDataTypes[i++] = prop.getDataType();
                        } else {
                            parameterDataTypes[i++] = DataType.OBJECT;
                        }
                    }
                }
            }
            annotationBuilder.member(DataMethod.META_MEMBER_PARAMETER_TYPE_DEFS, parameterDataTypes);
        }
    }

    private SourcePersistentProperty findProp(MethodMatchContext matchContext, String name) {
        SourcePersistentEntity rootEntity = matchContext.getRootEntity();
        return findProp(rootEntity, name);
    }

    private SourcePersistentProperty findProp(SourcePersistentEntity rootEntity, String name) {
        SourcePersistentProperty prop = rootEntity.getPropertyByName(name);
        if (prop == null) {
            SourcePersistentProperty identity = rootEntity.getIdentity();
            if (identity != null && identity.getName().equals(name)) {
                prop = identity;
            }
        }
        return prop;
    }

    private AnnotationValue<?>[] parameterBindingToAnnotationValues(Map<String, String> finalParameterBinding) {
        AnnotationValue<?>[] annotationParameters = new AnnotationValue[finalParameterBinding.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : finalParameterBinding.entrySet()) {
            annotationParameters[i++] = AnnotationValue.builder(Property.class)
                    .member("name", entry.getKey())
                    .member("value", entry.getValue())
                    .build();
        }
        return annotationParameters;
    }

    private List<MethodCandidate> initializeMethodCandidates(VisitorContext context) {
        List<MethodCandidate> finderList = Arrays.asList(
                new FindByFinder(),
                new ExistsByFinder(),
                new SaveEntityMethod(),
                new SaveOneMethod(),
                new SaveAllMethod(),
                new ListMethod(),
                new CountMethod(),
                new DeleteByMethod(),
                new DeleteMethod(),
                new CountByMethod(),
                new UpdateMethod(),
                new UpdateByMethod(),
                new ListSliceMethod(),
                new FindSliceByMethod(),
                new ListSliceMethod(),
                new FindPageByMethod(),
                new ListPageMethod(),
                new FindOneMethod(),
                new FindByIdsMethod()
        );
        SoftServiceLoader<MethodCandidate> otherCandidates = SoftServiceLoader.load(MethodCandidate.class);
        for (ServiceDefinition<MethodCandidate> definition : otherCandidates) {
            if (definition.isPresent()) {
                try {
                    finderList.add(definition.load());
                } catch (Exception e) {
                    context.warn("Could not load Data method candidate [" + definition.getName() + "]: " + e.getMessage(), null);
                }
            }
        }
        OrderUtil.sort(finderList);
        return finderList;
    }

    private @Nullable String resolveIdType(PersistentEntity entity) {
        Map<String, ClassElement> typeArguments = currentRepository.getTypeArguments(GenericRepository.class);
        String varName = "ID";
        if (typeArguments.isEmpty()) {
            typeArguments = currentRepository.getTypeArguments(SPRING_REPO);
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

    private @Nullable SourcePersistentEntity resolvePersistentEntity(MethodElement element, Map<String, Element> parametersInRole, VisitorContext context) {
        ClassElement returnType = element.getGenericReturnType();
        SourcePersistentEntity entity = resolvePersistentEntity(returnType);
        if (entity == null) {
            entity = resolveEntityForCurrentClass();
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
            context.fail("Could not resolved root entity. Either implement the Repository interface or define the entity as part of the signature", element);
            return null;
        }
    }

    private SourcePersistentEntity resolveEntityForCurrentClass() {
        SourcePersistentEntity entity = null;
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
        return entity;
    }

    private SourcePersistentEntity resolvePersistentEntity(ClassElement returnType) {
        if (returnType != null) {
            if (returnType.hasAnnotation(MappedEntity.class) || returnType.hasStereotype(Embeddable.class)) {
                return entityResolver.apply(returnType);
            } else {
                Collection<ClassElement> typeArguments = returnType.getTypeArguments().values();
                for (ClassElement typeArgument : typeArguments) {
                    SourcePersistentEntity entity = resolvePersistentEntity(typeArgument);
                    if (entity != null) {
                        return entity;
                    }
                }
            }
        }
        return null;
    }

}
