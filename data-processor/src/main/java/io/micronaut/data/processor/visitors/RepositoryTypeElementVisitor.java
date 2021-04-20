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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.*;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.*;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.*;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.finders.*;
import io.micronaut.data.processor.visitors.finders.page.FindPageByMethod;
import io.micronaut.data.processor.visitors.finders.page.ListPageMethod;
import io.micronaut.data.processor.visitors.finders.slice.FindSliceByMethod;
import io.micronaut.data.processor.visitors.finders.slice.ListSliceMethod;
import io.micronaut.data.processor.visitors.finders.specification.CountSpecificationMethod;
import io.micronaut.data.processor.visitors.finders.specification.FindAllSpecificationMethod;
import io.micronaut.data.processor.visitors.finders.specification.FindOneSpecificationMethod;
import io.micronaut.data.processor.visitors.finders.specification.FindPageSpecificationMethod;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.inject.ast.*;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static io.micronaut.data.model.query.builder.QueryBuilder.VARIABLE_PATTERN;

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

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
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
                    queryEncoder,
                    currentRepository,
                    context,
                    element,
                    typeRoles,
                    genericReturnType,
                    parameters
            );
            for (MethodCandidate finder : finders) {
                boolean matches;
                try {
                    matches = finder.isMethodMatch(element, matchContext);
                } catch (MatchFailedException e) {
                    return;
                }
                try {
                    if (matches) {
                        SourcePersistentEntity entity = resolvePersistentEntity(element, parametersInRole, context);

                        if (entity == null) {
                            matchContext.fail("Unable to establish persistent entity to query for method: " + element.getName());
                            this.failing = matchContext.isFailing();
                            return;
                        }

                        String idType = resolveIdType(entity);

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
                        MethodMatchInfo methodInfo;
                        try {
                            methodInfo = finder.buildMatchInfo(methodMatchContext);
                        } catch (MatchFailedException ex) {
                            this.failing = true;
                            return;
                        } catch (Exception e) {
                            e.printStackTrace();
                            matchContext.fail(e.getMessage());
                            this.failing = true;
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
                            List<QueryParameterBinding> parameterBinding = null;
                            boolean rawCount = false;
                            boolean encodeEntityParameters = false;
                            boolean supportsImplicitQueries = matchContext.supportsImplicitQueries();
                            if (queryObject != null) {
                                if (queryObject instanceof RawQuery) {
                                    RawQuery rawQuery = (RawQuery) queryObject;
                                    // no need to annotation since already annotated, just replace the
                                    // the computed parameter names
                                    parameterBinding = rawQuery.getParameterBinding();

                                    if (matchContext.isTypeInRole(genericReturnType, TypeRole.PAGE) || element.isPresent(io.micronaut.data.annotation.Query.class, "countQuery")) {
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
                                    encodeEntityParameters = rawQuery.isEncodeEntityParameters();
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
                                                final boolean isEntityArgument = parameters.length == 1 &&
                                                        (
                                                                TypeUtils.isIterableOfEntity(parameters[0].getGenericType()) ||
                                                                parameters[0].getGenericType().getName().equals(entity.getName())
                                                        );
                                                if (isEntityArgument) {
                                                    encodeEntityParameters = true;
                                                }
                                                encodedQuery = queryEncoder
                                                        .buildUpdate(
                                                                annotationMetadataHierarchy,
                                                                queryObject,
                                                                methodInfo.getUpdateProperties());
                                                break;
                                            case INSERT:
                                                encodedQuery = queryEncoder
                                                        .buildInsert(annotationMetadataHierarchy, entity);
                                                encodeEntityParameters = true;
                                            break;
                                            case QUERY:
                                            default:

                                                encodedQuery = queryEncoder.buildQuery(
                                                        annotationMetadataHierarchy,
                                                        queryObject
                                                );
                                        }

                                    } catch (Exception e) {
                                        if (currentRepository != null) {
                                            methodMatchContext.fail("Invalid query method [" + element.getName() + "] of repository [" + currentRepository.getName() + "]: " + e.getMessage());
                                        } else {
                                            methodMatchContext.fail("Invalid query method [" + element.getName() + "]: " + e.getMessage());
                                        }
                                        this.failing = true;
                                        return;
                                    }

                                    if (encodedQuery != null) {
                                        parameterBinding = encodedQuery.getParameterBindings();
                                        bindAdditionalParameters(methodMatchContext, parameterBinding, parameters, encodedQuery.getAdditionalRequiredParameters());

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
                                            for (JoinPath joinPath : queryObject.getJoinPaths()) {
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
                            }

                            ClassElement runtimeInterceptor = methodInfo.getRuntimeInterceptor();

                            if (runtimeInterceptor != null) {
                                List<QueryParameterBinding> finalParameterBinding = parameterBinding;
                                QueryResult finalPreparedCount1 = preparedCount;
                                boolean finalRawCount = rawCount;
                                boolean finalEncodeEntityParameters = encodeEntityParameters;
                                element.annotate(DataMethod.class, annotationBuilder -> {
                                    annotationBuilder.member(DataMethod.META_MEMBER_ROOT_ENTITY, new AnnotationClassValue<>(entity.getName()));

                                    // include the roles
                                    methodInfo.getParameterRoles()
                                            .forEach(annotationBuilder::member);

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
                                    if (idType != null) {
                                        annotationBuilder.member(DataMethod.META_MEMBER_ID_TYPE, idType);
                                    }
                                    annotationBuilder.member(DataMethod.META_MEMBER_INTERCEPTOR, new AnnotationClassValue<>(runtimeInterceptor.getName()));

                                    if (CollectionUtils.isNotEmpty(finalParameterBinding)) {
                                        if (!supportsImplicitQueries && !finalEncodeEntityParameters) {
                                            annotationBuilder.member(DataMethod.META_MEMBER_PARAMETER_TYPE_DEFS, getDataTypes(methodMatchContext, parameters, finalParameterBinding));
                                        }

                                        if (finalEncodeEntityParameters) {
                                            annotationBuilder.member(
                                                    DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS,
                                                    finalParameterBinding.stream()
                                                            .map(binding -> {
                                                                if (binding.isAutoPopulatedUpdatable() && binding.getQueryParameter() != null) {
                                                                    return "";
                                                                }
                                                                if (binding.getQueryParameter() != null) {
                                                                    return binding.getQueryParameter().getName();
                                                                }
                                                                return binding.getPath();
                                                            })
                                                            .toArray(String[]::new)
                                            );
                                            annotationBuilder.member(
                                                    DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_PATHS,
                                                    finalParameterBinding.stream()
                                                            .map(binding -> {
                                                                if (binding.isAutoPopulatedUpdatable() && binding.getQueryParameter() != null) {
                                                                    return binding.getQueryParameter().getName();
                                                                }
                                                                return "";
                                                            })
                                                            .toArray(String[]::new)
                                            );
                                        } else if (finalRawCount) {
                                            element.stringValue(Query.class, "countQuery").ifPresent(cq -> element.annotate(
                                                    Query.class,
                                                    (builder) -> builder.member(DataMethod.META_MEMBER_RAW_COUNT_QUERY, replaceNamedParameters(queryEncoder, cq))
                                            ));
                                            parameterBindingToIndex(
                                                    annotationBuilder,
                                                    parameters,
                                                    finalParameterBinding,
                                                    methodInfo,
                                                    supportsImplicitQueries,
                                                    DataMethod.META_MEMBER_COUNT_PARAMETERS,
                                                    DataMethod.META_MEMBER_PARAMETER_BINDING);
                                        } else {
                                            parameterBindingToIndex(
                                                    annotationBuilder,
                                                    parameters,
                                                    finalParameterBinding,
                                                    methodInfo,
                                                    supportsImplicitQueries,
                                                    DataMethod.META_MEMBER_PARAMETER_BINDING
                                            );
                                        }
                                    }
                                    if (finalPreparedCount1 != null) {
                                        List<QueryParameterBinding> countParameterBindings = finalPreparedCount1.getParameterBindings();
                                        bindAdditionalParameters(methodMatchContext, countParameterBindings, parameters, finalPreparedCount1.getAdditionalRequiredParameters());
                                        parameterBindingToIndex(
                                                annotationBuilder,
                                                parameters,
                                                countParameterBindings,
                                                methodInfo,
                                                supportsImplicitQueries,
                                                DataMethod.META_MEMBER_COUNT_PARAMETERS
                                        );
                                    }

                                    Arrays.stream(parameters)
                                            .filter(p -> p.getGenericType().isAssignable(entity.getName()))
                                            .findFirst()
                                            .ifPresent(parameterElement -> annotationBuilder.member(DataMethod.META_MEMBER_ENTITY, parameterElement.getName()));

                                    for (Map.Entry<String, String> entry : methodInfo.getParameterRoles().entrySet()) {
                                        annotationBuilder.member(entry.getKey(), entry.getValue());
                                    }
                                    if (queryObject != null) {
                                        if (queryObject instanceof RawQuery) {
                                            element.annotate(Query.class, (builder) -> builder.member(DataMethod.META_MEMBER_RAW_QUERY,
                                                    element.stringValue(Query.class)
                                                            .map(q -> replaceNamedParameters(queryEncoder, q))
                                                            .orElse(null)));
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
                            } else {
                                methodMatchContext.fail("Unable to implement Repository method: " + currentRepository.getSimpleName() + "." + element.getName() + "(..). No possible runtime implementations found.");
                                this.failing = true;
                            }
                            return;
                        }
                        this.failing = methodMatchContext.isFailing();
                    }
                } catch (MatchFailedException e) {
                    this.failing = true;
                    return;
                }
            }

            this.failing = true;

            if (matchContext.isPossiblyFailing()) {
                matchContext.logPossibleFailures();
            } else {
                String messageStart = matchContext.getUnableToImplementMessage();
                context.fail(messageStart + "No possible implementations found.", element);
            }
        }
    }

    private DataType[] getDataTypes(MethodMatchContext methodMatchContext, ParameterElement[] parameters, List<QueryParameterBinding> finalParameterBinding) {
        Map<String, DataType> parametersTypes = Arrays.stream(parameters)
                .map(p -> new AbstractMap.SimpleEntry<>(p.getName(), TypeUtils.resolveDataType(p).orElse(null)))
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

        return finalParameterBinding.stream()
                .map(queryParameterBinding -> {
                    if (queryParameterBinding.getQueryParameter() != null) {
                        DataType dataType = parametersTypes.get(queryParameterBinding.getQueryParameter().getName());
                        if (dataType != null) {
                            return dataType;
                        }
                    }
                    if (queryParameterBinding.getDataType() != DataType.OBJECT) {
                        return queryParameterBinding.getDataType();
                    }
                    return resolvePropertyDataType(methodMatchContext, parameters, queryParameterBinding.getPath());
                }).toArray(DataType[]::new);
    }

    private void bindAdditionalParameters(MethodMatchContext methodMatchContext, List<QueryParameterBinding> parameterBinding, ParameterElement[] parameters, Map<String, String> params) {
        if (CollectionUtils.isNotEmpty(params)) {
            for (Map.Entry<String, String> param : params.entrySet()) {
                ParameterElement parameter = Arrays.stream(parameters)
                        .filter(p -> p.stringValue(Parameter.class).orElse(p.getName()).equals(param.getValue()))
                        .findFirst().orElse(null);
                if (parameter == null) {
                    methodMatchContext.failAndThrow("A @Where(..) definition requires a parameter called [" + param.getValue() + "] which is not present in the method signature.");
                    return;
                }
                parameterBinding.add(QueryParameterBinding.of(
                        param.getKey(),
                        param.getValue(),
                        resolvePropertyDataType(methodMatchContext, parameters, param.getValue()),
                        new QueryParameter(parameter.getName()),
                        false
                ));
            }
        }
    }

    private String replaceNamedParameters(QueryBuilder queryEncoder, String query) {
        if (queryEncoder instanceof SqlQueryBuilder && StringUtils.isNotEmpty(query)) {
            SqlQueryBuilder sqlQueryBuilder = (SqlQueryBuilder) queryEncoder;
            Matcher matcher = VARIABLE_PATTERN.matcher(query);
            boolean result = matcher.find();
            int i = 1;
            if (result) {
                StringBuffer sb = new StringBuffer();
                do {
                    String name = sqlQueryBuilder.formatParameter(i++).getName();
                    matcher.appendReplacement(sb, "$1" + Matcher.quoteReplacement(name));
                    result = matcher.find();
                } while (result);
                matcher.appendTail(sb);
                return sb.toString();
            }
        }
        return query;
    }

    private void parameterBindingToIndex(
            AnnotationValueBuilder<DataMethod> annotationBuilder,
            ParameterElement[] parameters,
            List<QueryParameterBinding> parameterBindings,
            MethodMatchInfo methodMatchInfo,
            boolean includeNames,
            String... members) {
        List<String> parameterNames = Arrays.stream(parameters)
                .map(parameterElement -> parameterElement.stringValue(Parameter.class).orElse(parameterElement.getName()))
                .collect(Collectors.toList());
        int len = parameterBindings.size();
        String[] parameterPaths = new String[len];
        String[] nameIndex = new String[len];
        String[] autoPopulatedPropertyPaths = new String[len];
        String[] autoPopulatedPreviousProperties = new String[len];
        int[] autoPopulatedPreviousPropertyIndexes = new int[len];
        int[] parameterIndices = new int[len];
        Arrays.fill(parameterPaths, "");
        Arrays.fill(autoPopulatedPropertyPaths, "");
        Arrays.fill(autoPopulatedPreviousProperties, "");
        Arrays.fill(parameterIndices, -1);
        Arrays.fill(autoPopulatedPreviousPropertyIndexes, -1);
        Collection<String> parametersInRole = methodMatchInfo.getParameterRoles().values();
        int pathIndex = 0;
        for (QueryParameterBinding parameterBinding : parameterBindings) {
            nameIndex[pathIndex] = parameterBinding.getKey();
            QueryParameter queryParameter = parameterBinding.getQueryParameter();
            if (queryParameter != null) {
                bindParam(parameterNames, parameterPaths, parameterIndices, pathIndex, queryParameter.getName(), false);
            } else {
                if (parameterBinding.isAutoPopulatedUpdatable()) {
                    autoPopulatedPropertyPaths[pathIndex] = parameterBinding.getPath();
                    int finalPathIndex = pathIndex;
                    parameterBindings.stream()
                            .filter(p -> p != parameterBinding && p.getPath().equals(parameterBinding.getPath()) && p.getQueryParameter() != null)
                            .findFirst()
                            .ifPresent(p -> bindParam(parameterNames, autoPopulatedPreviousProperties, autoPopulatedPreviousPropertyIndexes, finalPathIndex, p.getQueryParameter().getName(), true));
                } else {
                    boolean found = false;
                    for (String name : parametersInRole) {
                        if (name.equals(parameterBinding.getPath())) {
                            parameterPaths[pathIndex] = name;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        bindParam(parameterNames, parameterPaths, parameterIndices, pathIndex, parameterBinding.getPath(), false);
                    }
                }
            }
            pathIndex++;
        }
        for (String member : members) {
            annotationBuilder.member(member, parameterIndices);
            annotationBuilder.member(member + "Paths", parameterPaths);
            annotationBuilder.member(member + "AutoPopulatedPaths", autoPopulatedPropertyPaths);
            annotationBuilder.member(member + "AutoPopulatedPreviousPaths", autoPopulatedPreviousProperties);
            annotationBuilder.member(member + "AutoPopulatedPrevious", autoPopulatedPreviousPropertyIndexes);
            if (includeNames) {
                annotationBuilder.member(member + "Names", nameIndex);
            }
        }
    }

    private void bindParam(List<String> parameterNames, String[] parameterPaths, int[] parameterIndices, int pathIndex, String parameterName, boolean setNotFoundPath) {
        int i = parameterNames.indexOf(parameterName);
        if (i > -1) {
            parameterIndices[pathIndex] = i;
        } else {
            int j = parameterName.indexOf('.');
            if (j > -1) {
                String prop = parameterName.substring(0, j);
                int paramIndex = parameterNames.indexOf(prop);
                if (paramIndex > -1) {
                    parameterPaths[pathIndex] = paramIndex + "." + parameterName.substring(j + 1);
                }
            } else if (setNotFoundPath) {
                Objects.requireNonNull(parameterName);
                parameterPaths[pathIndex] = parameterName;
            }
        }
    }

    private DataType resolvePropertyDataType(MethodMatchContext matchContext, ParameterElement[] parameters, String value) {
        Map<String, ParameterElement> paramMap = Arrays.stream(parameters).collect(Collectors.toMap(Element::getName, p -> p));
        int dot = value.indexOf('.');
        if (dot > -1) {
            String val = value.substring(0, dot);
            ParameterElement parameterElement = paramMap.get(val);
            if (parameterElement != null) {
                SourcePersistentEntity sourcePersistentEntity = resolvePersistentEntity(parameterElement.getGenericType());
                if (sourcePersistentEntity != null) {
                    String name = value.substring(dot + 1);
                    PersistentProperty subProp = sourcePersistentEntity.getPropertyByPath(name).orElse(null);
                    if (subProp != null && !(subProp instanceof Association)) {
                        return subProp.getDataType();
                    }
                    ClassElement type = parameterElement.getType();
                    if (TypeUtils.isContainerType(type)) {
                        type = type.getFirstTypeArgument().orElse(type);
                    }
                    return TypeUtils.resolveDataType(type, dataTypes);
                }
            }
        } else {
            ParameterElement parameterElement = paramMap.get(value);
            if (parameterElement != null) {
                DataType dataType = TypeUtils.resolveDataType(parameterElement).orElse(null);
                if (dataType != null) {
                    return dataType;
                }
            }
            SourcePersistentProperty prop = matchContext.getRootEntity().getPropertyByName(value);
            if (prop != null && !(prop instanceof Association)) {
                return prop.getDataType();
            }
            if (parameterElement != null) {
                ClassElement type = parameterElement.getType();
                if (TypeUtils.isContainerType(type)) {
                    type = type.getFirstTypeArgument().orElse(type);
                }
                return TypeUtils.resolveDataType(type, dataTypes);
            }
        }
        return DataType.OBJECT;
    }

    private List<MethodCandidate> initializeMethodCandidates(VisitorContext context) {
        List<MethodCandidate> finderList = Arrays.asList(
                new RawQueryMethod(),
                new FindByFinder(),
                new ExistsByFinder(),
                new SaveEntityMethod(),
                new SaveOneMethod(),
                new ListMethod(),
                new CountMethod(),
                new DeleteByMethod(),
                new DeleteMethod(),
                new CountByMethod(),
                new UpdateMethod(),
                new UpdateEntityMethod(),
                new UpdateByMethod(),
                new ListSliceMethod(),
                new FindSliceByMethod(),
                new ListSliceMethod(),
                new FindPageByMethod(),
                new ListPageMethod(),
                new FindOneMethod(),
                new FindByIdsMethod(),
                new FindOneSpecificationMethod(),
                new CountSpecificationMethod(),
                new FindAllSpecificationMethod(),
                new FindPageSpecificationMethod()
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
        SourcePersistentEntity entity = resolveEntityForCurrentClass();
        if (entity == null) {
            entity = resolvePersistentEntity(returnType);
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
