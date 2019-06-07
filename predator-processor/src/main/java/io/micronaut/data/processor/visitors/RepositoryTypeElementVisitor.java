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
package io.micronaut.data.processor.visitors;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Persisted;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.model.*;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.finders.*;
import io.micronaut.data.processor.visitors.finders.page.FindPageByMethod;
import io.micronaut.data.processor.visitors.finders.page.ListPageMethod;
import io.micronaut.data.processor.visitors.finders.page.QueryPageMethod;
import io.micronaut.data.processor.visitors.finders.slice.FindSliceByMethod;
import io.micronaut.data.processor.visitors.finders.slice.ListSliceMethod;
import io.micronaut.data.processor.visitors.finders.slice.QuerySliceMethod;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.inject.ast.*;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.*;
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
    private QueryBuilder queryEncoder;
    private Map<String, String> typeRoles = new HashMap<>();
    private List<MethodCandidate> finders;
    private boolean failing = false;

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
        if (failing) {
            return;
        }
        this.currentClass = element;

        queryEncoder = resolveQueryEncoder(element, context);
        AnnotationValue[] roleArray = element.getAnnotationMetadata()
                .getValue(Repository.class, "typeRoles", AnnotationValue[].class).orElse(new AnnotationValue[0]);
        for (AnnotationValue<?> parameterRole : roleArray) {
            String role = parameterRole.get("role", String.class).orElse(null);
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
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (currentClass == null || failing) {
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

            MatchContext matchContext = new MatchContext(
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
                            entity,
                            context,
                            genericReturnType,
                            element,
                            parametersInRole,
                            typeRoles,
                            parameters
                    );
                    MethodMatchInfo methodInfo = finder.buildMatchInfo(methodMatchContext);
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
                        QueryResult preparedCount = null;
                        Map<String, String> parameterBinding = null;
                        boolean rawCount = false;
                        if (queryObject != null) {
                            if (queryObject instanceof RawQuery) {
                                RawQuery rawQuery = (RawQuery) queryObject;

                                // no need to annotation since already annotated, just replace the
                                // the computed parameter names
                                parameterBinding = rawQuery.getParameterBinding();
                                if (matchContext.isTypeInRole(genericReturnType, TypeRole.PAGE)) {
                                    String cq = matchContext.getAnnotationMetadata().getValue(io.micronaut.data.annotation.Query.class, "countQuery", String.class)
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
                                QueryResult encodedQuery;
                                try {
                                    switch (methodInfo.getOperationType()) {
                                        case DELETE:
                                            encodedQuery = queryEncoder.buildDelete(queryObject);
                                            break;
                                        case UPDATE:
                                            encodedQuery = queryEncoder
                                                    .buildUpdate(
                                                            queryObject,
                                                            methodInfo.getUpdateProperties());
                                            break;
                                        case INSERT:
                                            // TODO
                                        default:
                                            encodedQuery = queryEncoder.buildQuery(queryObject);
                                    }

                                } catch (Exception e) {
                                    methodMatchContext.fail("Invalid query method: " + e.getMessage());
                                    this.failing = true;
                                    return;
                                }

                                parameterBinding = encodedQuery.getParameters();

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
                                                annotationBuilder.member(PredatorMethod.META_MEMBER_COUNT_QUERY, finalPreparedCount.getQuery());
                                            }
                                    );
                                } else {
                                    element.annotate(io.micronaut.data.annotation.Query.class, annotationBuilder ->
                                            annotationBuilder.value(encodedQuery.getQuery())
                                    );
                                }
                            }
                        }

                        Class<? extends PredatorInterceptor> runtimeInterceptor = methodInfo.getRuntimeInterceptor();

                        if (runtimeInterceptor != null) {
                            Map<String, String> finalParameterBinding = parameterBinding;
                            QueryResult finalPreparedCount1 = preparedCount;
                            boolean finalRawCount = rawCount;
                            element.annotate(PredatorMethod.class, annotationBuilder -> {
                                annotationBuilder.member(PredatorMethod.META_MEMBER_ROOT_ENTITY, new AnnotationClassValue<>(entity.getName()));

                                // include the roles
                                methodInfo.getParameterRoles()
                                        .forEach(annotationBuilder::member);

                                if (methodInfo.isDto()) {
                                    annotationBuilder.member(PredatorMethod.META_MEMBER_DTO, true);
                                }

                                TypedElement resultType = methodInfo.getResultType();
                                if (resultType != null) {
                                    annotationBuilder.member(PredatorMethod.META_MEMBER_RESULT_TYPE, new AnnotationClassValue<>(resultType.getName()));
                                }
                                if (idType != null) {
                                    annotationBuilder.member(PredatorMethod.META_MEMBER_ID_TYPE, idType);
                                }
                                annotationBuilder.member(PredatorMethod.META_MEMBER_INTERCEPTOR, runtimeInterceptor);
                                if (finalParameterBinding != null) {
                                    AnnotationValue<?>[] annotationParameters = parameterBindingToAnnotationValues(finalParameterBinding);
                                    annotationBuilder.member(PredatorMethod.META_MEMBER_PARAMETER_BINDING, annotationParameters);
                                    if (finalRawCount) {
                                        annotationBuilder.member(PredatorMethod.META_MEMBER_COUNT_PARAMETERS, annotationParameters);
                                    }
                                }
                                if (finalPreparedCount1 != null) {
                                    AnnotationValue<?>[] annotationParameters = parameterBindingToAnnotationValues(finalPreparedCount1.getParameters());
                                    annotationBuilder.member(PredatorMethod.META_MEMBER_COUNT_PARAMETERS, annotationParameters);
                                }

                                Optional<ParameterElement> entityParam = Arrays.stream(parameters).filter(p -> {
                                    ClassElement t = p.getGenericType();
                                    return t.isAssignable(entity.getName());
                                }).findFirst();
                                entityParam.ifPresent(parameterElement -> annotationBuilder.member(PredatorMethod.META_MEMBER_ENTITY, parameterElement.getName()));

                                for (Map.Entry<String, String> entry : methodInfo.getParameterRoles().entrySet()) {
                                    annotationBuilder.member(entry.getKey(), entry.getValue());
                                }
                                if (queryObject != null) {
                                    int max = queryObject.getMax();
                                    if (max > -1) {
                                        annotationBuilder.member(PredatorMethod.META_MEMBER_PAGE_SIZE, max);
                                    }
                                    long offset = queryObject.getOffset();
                                    if (offset > 0) {
                                        annotationBuilder.member(PredatorMethod.META_MEMBER_PAGE_INDEX, offset);
                                    }
                                }
                            });
                            return;
                        } else {
                            methodMatchContext.fail("Unable to implement Repository method: " + currentClass.getSimpleName() + "." + element.getName() + "(..). No possible runtime implementations found.");
                            this.failing = true;
                            return;
                        }
                    }

                    this.failing = methodMatchContext.isFailing();

                }
            }

            this.failing = true;
            context.fail("Unable to implement Repository method: " + currentClass.getSimpleName() + "." + element.getName() + "(..). No possible implementations found.", element);
        }
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
                new QueryListMethod(),
                new QueryOneMethod(),
                new CountByMethod(),
                new UpdateMethod(),
                new UpdateByMethod(),
                new ListSliceMethod(),
                new FindSliceByMethod(),
                new ListSliceMethod(),
                new QuerySliceMethod(),
                new FindPageByMethod(),
                new ListPageMethod(),
                new QueryPageMethod(),
                new FindOneMethod(),
                new FindByIdsMethod()
        );
        SoftServiceLoader<MethodCandidate> otherCandidates = SoftServiceLoader.load(MethodCandidate.class);
        for (ServiceDefinition<MethodCandidate> definition : otherCandidates) {
            if (definition.isPresent()) {
                try {
                    finderList.add(definition.load());
                } catch (Exception e) {
                    context.warn("Could not load Predator method candidate [" + definition.getName() + "]: " + e.getMessage(), null);
                }
            }
        }
        OrderUtil.sort(finderList);
        return finderList;
    }

    private @Nullable String resolveIdType(PersistentEntity entity) {
        Map<String, ClassElement> typeArguments = currentClass.getTypeArguments(GenericRepository.class);
        String varName = "ID";
        if (typeArguments.isEmpty()) {
            typeArguments = currentClass.getTypeArguments(SPRING_REPO);
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
            Map<String, ClassElement> typeArguments = currentClass.getTypeArguments(GenericRepository.class);
            String argName = "E";
            if (typeArguments.isEmpty()) {
                argName = "T";
                typeArguments = currentClass.getTypeArguments(SPRING_REPO);
            }
            if (!typeArguments.isEmpty()) {
                ClassElement ce = typeArguments.get(argName);
                if (ce != null) {
                    entity = new SourcePersistentEntity(ce);
                }
            }
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

    private SourcePersistentEntity resolvePersistentEntity(ClassElement returnType) {
        if (returnType != null) {
            if (returnType.hasAnnotation(Persisted.class)) {
                return new SourcePersistentEntity(returnType);
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

    private QueryBuilder resolveQueryEncoder(Element element, VisitorContext context) {
        return element.getValue(
                Repository.class,
                PredatorMethod.META_MEMBER_QUERY_BUILDER,
                String.class
        ).flatMap(type -> {
            Object o = InstantiationUtils.tryInstantiate(type, RepositoryTypeElementVisitor.class.getClassLoader()).orElse(null);
            if (o instanceof QueryBuilder) {
                return Optional.of((QueryBuilder) o);
            } else {
                context.fail("QueryEncoder of type [" + type + "] not present on annotation processor path", element);
                return Optional.empty();
            }
        }).orElse(new JpaQueryBuilder());
    }
}
