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
import org.jspecify.annotations.Nullable;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Delete;
import io.micronaut.data.annotation.EntityRepresentation;
import io.micronaut.data.annotation.Insert;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.ParameterExpression;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.annotation.Update;
import io.micronaut.data.annotation.sql.Procedure;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.intercept.annotation.DataMethodQuery;
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.Slice;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.AdditionalParameterBinding;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.QueryOutParameterBinding;
import io.micronaut.data.intercept.annotation.DataMethodQueryOutParameter;
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions;
import io.micronaut.data.model.query.builder.sql.SqlQueryConfiguration;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.model.criteria.impl.SourceParameterExpressionImpl;
import io.micronaut.data.processor.visitors.finders.FindersUtils;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.MethodMatcher;
import io.micronaut.data.processor.visitors.finders.RawQueryMethodMatcher;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.repository.jpa.criteria.CriteriaDeleteBuilder;
import io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder;
import io.micronaut.data.repository.jpa.criteria.CriteriaUpdateBuilder;
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification;
import io.micronaut.inject.annotation.EvaluatedExpressionReferenceCounter;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.ast.UnresolvedTypeKind;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.ElementPostponedToNextRoundException;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    public static final String JAKARTA_DATA_REPO = "jakarta.data.repository.DataRepository";
    private static final String JPA_SPECIFICATION_EXECUTOR = "io.micronaut.data.repository.jpa.JpaSpecificationExecutor";
    private static final String ASYNC_JPA_SPECIFICATION_EXECUTOR = "io.micronaut.data.repository.jpa.async.AsyncJpaSpecificationExecutor";
    private static final String REACTIVE_STREAMS_JPA_SPECIFICATION_EXECUTOR = "io.micronaut.data.repository.jpa.reactive.ReactiveStreamsJpaSpecificationExecutor";
    private static final String REACTOR_JPA_SPECIFICATION_EXECUTOR = "io.micronaut.data.repository.jpa.reactive.ReactorJpaSpecificationExecutor";
    private static final String JDBC_REPO_ANNOTATION = "io.micronaut.data.jdbc.annotation.JdbcRepository";
    private static final String R2DBC_REPO_ANNOTATION = "io.micronaut.data.r2dbc.annotation.R2dbcRepository";
    private static final String DIALECT_ATTR = "dialect";
    private static final boolean IS_DOCUMENT_ANNOTATION_PROCESSOR = ClassUtils.isPresent("io.micronaut.data.document.processor.mapper.MappedEntityMapper", RepositoryTypeElementVisitor.class.getClassLoader());
    private static final Map<String, String> COMMON_TYPE_ROLES;
    private static final List<Map.Entry<String, String>> COMMON_ANNOTATION_ROLES;

    private final List<MethodMatcher> methodsMatchers;
    private final Set<String> visitedRepositories = new HashSet<>();
    private final Set<String> postponedRepositories = new HashSet<>();
    private Map<String, DataType> dataTypes = Collections.emptyMap();
    private final Map<String, SourcePersistentEntity> entityMap = new HashMap<>(50);


    {
        List<MethodMatcher> matcherList = new ArrayList<>(20);
        SoftServiceLoader.load(MethodMatcher.class, RepositoryTypeElementVisitor.class.getClassLoader()).collectAll(matcherList);
        OrderUtil.sort(matcherList);
        methodsMatchers = matcherList;
    }

    static {
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put(Pageable.class.getName(), TypeRole.PAGEABLE);
        roles.put(Sort.class.getName(), TypeRole.SORT);
        roles.put(CursoredPage.class.getName(), TypeRole.CURSORED_PAGE);
        roles.put(Page.class.getName(), TypeRole.PAGE);
        roles.put(Slice.class.getName(), TypeRole.SLICE);
        roles.put(Limit.class.getName(), TypeRole.LIMIT);

        // Specifications
        roles.put(PredicateSpecification.class.getName(), TypeRole.SPECIFICATION_PREDICATE);
        roles.put(DeleteSpecification.class.getName(), TypeRole.SPECIFICATION_DELETE);
        roles.put(CriteriaDeleteBuilder.class.getName(), TypeRole.SPECIFICATION_DELETE);
        roles.put(UpdateSpecification.class.getName(), TypeRole.SPECIFICATION_UPDATE);
        roles.put(CriteriaUpdateBuilder.class.getName(), TypeRole.SPECIFICATION_UPDATE);
        roles.put(QuerySpecification.class.getName(), TypeRole.SPECIFICATION_QUERY);
        roles.put(CriteriaQueryBuilder.class.getName(), TypeRole.SPECIFICATION_QUERY);

        // Spring Data
        roles.put("org.springframework.data.domain.Pageable", TypeRole.PAGEABLE);
        roles.put("org.springframework.data.domain.Page", TypeRole.PAGE);
        roles.put("org.springframework.data.domain.Slice", TypeRole.SLICE);
        roles.put("org.springframework.data.domain.Sort", TypeRole.SORT);
        roles.put("org.springframework.data.jpa.domain.Specification", TypeRole.SPECIFICATION_PREDICATE);
        roles.put("org.springframework.data.jpa.domain.UpdateSpecification", TypeRole.SPECIFICATION_UPDATE);
        roles.put("org.springframework.data.jpa.domain.DeleteSpecification", TypeRole.SPECIFICATION_DELETE);

        // Jakarta Data
        roles.put("jakarta.data.page.Page", TypeRole.PAGE);
        roles.put("jakarta.data.page.CursoredPage", TypeRole.CURSORED_PAGE);
        roles.put("jakarta.data.page.PageRequest", TypeRole.PAGEABLE);
        roles.put("jakarta.data.Order", TypeRole.SORT);
        roles.put("jakarta.data.Sort", TypeRole.SORT);
        roles.put("jakarta.data.Limit", TypeRole.LIMIT);
        roles.put("jakarta.data.restrict.Restriction", TypeRole.SPECIFICATION_PREDICATE);

        COMMON_TYPE_ROLES = Collections.unmodifiableMap(roles);

        COMMON_ANNOTATION_ROLES = List.of(
            Map.entry("jakarta.data.repository.Is", TypeRole.SPECIFICATION_CONSTRAINT)
        );
    }

    @Nullable
    private Map<String, String> currentClassTypeRoles;

    @Nullable
    private ClassElement jakartaDataConstraint;

    /**
     * Default constructor.
     */
    public RepositoryTypeElementVisitor() {
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        postponedRepositories.clear();
        visitedRepositories.clear();
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public TypeElementQuery query() {
        return TypeElementQuery.onlyClass();
    }

    private Map<ClassElement, FindInterceptorDef> createFindInterceptors(ClassElement element, VisitorContext visitorContext) {
        List<FindInterceptorDef> defaultInterceptors = FindersUtils.getDefaultInterceptors(visitorContext);
        List<FindInterceptorDef> interceptors = new ArrayList<>(defaultInterceptors);
        AnnotationValue<RepositoryConfiguration> repositoryConfiguration = element.getAnnotationMetadata()
            .getAnnotation(RepositoryConfiguration.class);
        if (repositoryConfiguration != null) {
            for (AnnotationValue<io.micronaut.data.annotation.FindInterceptorDef> interceptor : repositoryConfiguration
                .getAnnotations("findInterceptors", io.micronaut.data.annotation.FindInterceptorDef.class)) {
                interceptors.add(new FindInterceptorDef(
                    interceptor.stringValue("returnType").flatMap(visitorContext::getClassElement).orElseThrow(),
                    interceptor.booleanValue("isContainer").orElse(true),
                    interceptor.stringValue("interceptor").flatMap(visitorContext::getClassElement).orElseThrow()
                ));
            }
        }
        return interceptors.stream().collect(Collectors.toMap(FindInterceptorDef::returnType, e -> e));
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        try {
            jakartaDataConstraint = context.getClassElement("jakarta.data.constraint.Constraint").orElse(null);
            currentClassTypeRoles = new LinkedHashMap<>(COMMON_TYPE_ROLES);
            String interfaceName = element.getName();
            if (visitedRepositories.contains(interfaceName)) {
                // prevent duplicate visits
                return;
            }
            if (element.hasStereotype("io.micronaut.data.document.annotation.DocumentProcessorRequired") && !IS_DOCUMENT_ANNOTATION_PROCESSOR) {
                throw new ProcessingException(element, "Repository is required to be processed by the data-document-processor. " +
                    "Make sure it's included as a dependency to the annotation processor classpath!");
            }


            if (!element.hasDeclaredStereotype(Repository.class)) {
                return;
            }
            // delay visiting until the next round.
            if (element.hasUnresolvedTypes(UnresolvedTypeKind.INTERFACE, UnresolvedTypeKind.SUPERCLASS) && !postponedRepositories.contains(interfaceName)) {
                postponedRepositories.add(interfaceName);
                throw new ElementPostponedToNextRoundException(element);
            }
            visitedRepositories.add(interfaceName);
            configureSqlDialectOptions(element, context);
            QueryBuilder queryEncoder = newQueryBuilder(element.getAnnotationMetadata());
            this.dataTypes = Utils.getConfiguredDataTypes(element);
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
                        Objects.requireNonNull(currentClassTypeRoles).put(ce.getName(), role)
                    );
                }
            }

            Function<ClassElement, SourcePersistentEntity> entityResolver = new SourcePersistentEntityResolver(context, entityMap);
            Function<String, SourcePersistentEntity> entityBySimplyNameResolver = new Function<>() {
                @Override
                @Nullable
                public SourcePersistentEntity apply(String entitySimpleName) {
                    for (SourcePersistentEntity persistentEntity : entityMap.values()) {
                        if (persistentEntity.getPersistedName().equalsIgnoreCase(entitySimpleName)) {
                            return persistentEntity;
                        }
                    }
                    return null;
                }
            };
            // Annotate repository with EntityRepresentation if present on entity class
            annotateEntityRepresentationIfPresent(element, entityResolver);

            element.getMethods().forEach(method -> visitRepositoryMethod(queryEncoder, element, method, entityResolver, entityBySimplyNameResolver, context));
        } catch (Exception e) {
            visitedRepositories.remove(element.getName());
            throw e;
        }
    }

    private static void configureSqlDialectOptions(ClassElement element, VisitorContext context) {
        AnnotationMetadata annotationMetadata = element.getAnnotationMetadata();
        // Compile-time metadata can retain a class-valued meta-annotation member as its class name.
        boolean usesSqlQueryBuilder = annotationMetadata.classValue(
            RepositoryConfiguration.class,
            DataMethod.META_MEMBER_QUERY_BUILDER
        ).map(SqlQueryBuilder.class::equals).orElseGet(() -> annotationMetadata.stringValue(
            RepositoryConfiguration.class,
            DataMethod.META_MEMBER_QUERY_BUILDER
        ).filter(SqlQueryBuilder.class.getName()::equals).isPresent());
        if (!usesSqlQueryBuilder) {
            return;
        }
        Dialect dialect = annotationMetadata
            .enumValue(JDBC_REPO_ANNOTATION, DIALECT_ATTR, Dialect.class)
            .orElseGet(() ->
                annotationMetadata
                    .enumValue(R2DBC_REPO_ANNOTATION, DIALECT_ATTR, Dialect.class)
                    .orElseGet(() ->
                        annotationMetadata
                            .enumValue(Repository.class, DIALECT_ATTR, Dialect.class)
                            .orElse(Dialect.ANSI)));
        if (hasExplicitSqlDialectVersion(annotationMetadata, dialect)) {
            return;
        }
        String versionConfiguration = SqlDialectOptions.versionConfiguration(dialect);
        String version = annotationMetadata
            .stringValue(JDBC_REPO_ANNOTATION, SqlDialectOptions.MEMBER_VERSION)
            .filter(StringUtils::isNotEmpty)
            .or(() -> annotationMetadata
                .stringValue(R2DBC_REPO_ANNOTATION, SqlDialectOptions.MEMBER_VERSION)
                .filter(StringUtils::isNotEmpty))
            .orElseGet(() -> context.getOptions().get(versionConfiguration));
        if (StringUtils.isEmpty(version)) {
            version = System.getProperty(versionConfiguration);
        }
        if (StringUtils.isNotEmpty(version)) {
            if (SqlDialectOptions.of(dialect, version).version().isPresent()) {
                annotateSqlDialectVersion(element, annotationMetadata, dialect, version);
            } else {
                context.warn("Invalid SQL dialect target version '" + version + "' for dialect " + dialect
                    + ". Expected numeric major[.minor[.patch]] notation.", element);
            }
        }
    }

    private static boolean hasExplicitSqlDialectVersion(AnnotationMetadata annotationMetadata, Dialect dialect) {
        AnnotationValue<SqlQueryConfiguration> annotation = annotationMetadata.getAnnotation(SqlQueryConfiguration.class);
        if (annotation == null) {
            return false;
        }
        return annotation.getAnnotations(AnnotationMetadata.VALUE_MEMBER, SqlQueryConfiguration.DialectConfiguration.class)
            .stream()
            .filter(dialectConfig -> dialectConfig.enumValue(DIALECT_ATTR, Dialect.class)
                .filter(dialect::equals)
                .isPresent())
            .anyMatch(dialectConfig -> dialectConfig.stringValue(SqlDialectOptions.MEMBER_VERSION)
                .filter(StringUtils::isNotEmpty)
                .isPresent());
    }

    private static void annotateSqlDialectVersion(ClassElement element,
                                                  AnnotationMetadata annotationMetadata,
                                                  Dialect dialect,
                                                  String version) {
        AnnotationValue<SqlQueryConfiguration> annotation = annotationMetadata.getAnnotation(SqlQueryConfiguration.class);
        // Compiler options are not available at runtime. Preserve existing per-dialect settings while
        // materializing the target version into repository metadata for runtime query-builder reconstruction.
        List<AnnotationValue<SqlQueryConfiguration.DialectConfiguration>> dialectConfigs = annotation == null
            ? Collections.emptyList()
            : annotation.getAnnotations(AnnotationMetadata.VALUE_MEMBER, SqlQueryConfiguration.DialectConfiguration.class);
        List<AnnotationValue<SqlQueryConfiguration.DialectConfiguration>> updatedConfigs = new ArrayList<>(dialectConfigs.size() + 1);
        boolean updated = false;
        for (AnnotationValue<SqlQueryConfiguration.DialectConfiguration> dialectConfig : dialectConfigs) {
            if (dialectConfig.enumValue(DIALECT_ATTR, Dialect.class).filter(dialect::equals).isPresent()) {
                updatedConfigs.add(AnnotationValue.builder(SqlQueryConfiguration.DialectConfiguration.class)
                    .members(dialectConfig.getValues())
                    .member(SqlDialectOptions.MEMBER_VERSION, version)
                    .build());
                updated = true;
            } else {
                updatedConfigs.add(dialectConfig);
            }
        }
        if (!updated) {
            updatedConfigs.add(AnnotationValue.builder(SqlQueryConfiguration.DialectConfiguration.class)
                .member(DIALECT_ATTR, dialect)
                .member(SqlDialectOptions.MEMBER_VERSION, version)
                .build());
        }
        element.annotate(
            SqlQueryConfiguration.class,
            builder -> builder.values(updatedConfigs.toArray(AnnotationValue[]::new))
        );
    }

    /**
     * Build a query build from the configured annotation metadata.
     * @param annotationMetadata The annotation metadata.
     * @return The query builder
     */
    private static QueryBuilder newQueryBuilder(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.stringValue(
                RepositoryConfiguration.class,
                DataMethod.META_MEMBER_QUERY_BUILDER
        ).flatMap(type -> BeanIntrospector.SHARED.findIntrospections(ref -> ref.isPresent() && ref.getBeanType().getName().equals(type))
                .stream().findFirst()
                .map(introspection -> {
                    try {
                        Argument<?>[] constructorArguments = introspection.getConstructorArguments();
                        if (constructorArguments.length == 0) {
                            return (QueryBuilder) introspection.instantiate();
                        } else if (constructorArguments.length == 1 && constructorArguments[0].getType() == AnnotationMetadata.class) {
                            return (QueryBuilder) introspection.instantiate(annotationMetadata);
                        }
                    } catch (InstantiationException e) {
                        return new JpaQueryBuilder();
                    }
                    return new JpaQueryBuilder();
                })).orElse(new JpaQueryBuilder());
    }

    private void visitRepositoryMethod(QueryBuilder queryEncoder,
                                       ClassElement currentRepository,
                                       MethodElement method,
                                       Function<ClassElement, SourcePersistentEntity> entityResolver,
                                       Function<String, SourcePersistentEntity> entityBySimplyNameResolver,
                                       VisitorContext context) {
        Objects.requireNonNull(entityResolver);
        Objects.requireNonNull(entityBySimplyNameResolver);

        ClassElement genericReturnType = method.getGenericReturnType();
        if (method.isAbstract() && !method.isStatic()) {
            ParameterElement[] parameters = method.getParameters();
            Map<Element, String> parametersInRole = getParametersInRole(parameters);

            if (method.hasDeclaredAnnotation(DataMethod.class)) {
                // explicitly handled
                return;
            }

            Map<ClassElement, FindInterceptorDef> findInterceptors = createFindInterceptors(currentRepository, context);

            MatchContext matchContext = new MatchContext(
                queryEncoder,
                currentRepository,
                context,
                method,
                Objects.requireNonNull(currentClassTypeRoles),
                COMMON_ANNOTATION_ROLES,
                genericReturnType,
                parameters,
                findInterceptors);

            try {
                List<ParameterElement> parametersNotInRole = Arrays.stream(parameters)
                    .filter(p -> !parametersInRole.containsKey(p))
                    .toList();
                SourcePersistentEntity entity = resolvePersistentEntity(currentRepository, method, parametersInRole, parametersNotInRole, entityResolver);
                MethodMatchContext methodMatchContext = new MethodMatchContext(
                    queryEncoder,
                    currentRepository,
                    entity,
                    context,
                    genericReturnType,
                    method,
                    parametersInRole,
                    currentClassTypeRoles,
                    COMMON_ANNOTATION_ROLES,
                    parameters,
                    entityResolver,
                    findInterceptors,
                    entityBySimplyNameResolver
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
                    throw new ProcessingException(method, "Failures found.");
                } else {
                    String messageStart = matchContext.getUnableToImplementMessage();
                    throw new ProcessingException(method, messageStart + "No possible implementations found.");
                }
            } catch (MatchFailedException e) {
                throw new ProcessingException(e.getElement() == null ? method : e.getElement(), matchContext.getUnableToImplementMessage() + e.getMessage());
            } catch (Exception e) {
                e.printStackTrace(System.err);
                if (e instanceof ElementPostponedToNextRoundException || e.getClass().getSimpleName().equals("PostponeToNextRoundException")) {
                    // rethrow postponed and don't fail compilation
                    // this is not ideal since PostponeToNextRoundException is part of inject-java
                    throw e;
                }
                throw new ProcessingException(method, "Exception occurred while processing: " + e.getMessage(), e);
            }
        }
    }

    private Map<Element, String> getParametersInRole(ParameterElement[] parameters) {
        Map<Element, String> parametersInRole = new LinkedHashMap<>(2);
        for (ParameterElement parameter : parameters) {
            parametersInRole.computeIfAbsent(parameter, p -> {
                String typeRole = findTypeRole(parameter.getType());
                if (typeRole == null) {
                    typeRole = findAnnotationRole(parameter);
                }
                if (typeRole == null && jakartaDataConstraint != null && parameter.getType().isAssignable(jakartaDataConstraint)) {
                    return TypeRole.SPECIFICATION_CONSTRAINT;
                }
                return typeRole;
            });
        }
        return parametersInRole;
    }

    @Nullable
    private String findTypeRole(ClassElement type) {
        Set<Map.Entry<String, String>> entries = Objects.requireNonNull(currentClassTypeRoles).entrySet();
        for (Map.Entry<String, String> entry : entries) {
            // Find the role by the exact type name
            if (type.getName().equals(entry.getKey())) {
                return entry.getValue();
            }
        }
        for (Map.Entry<String, String> entry : entries) {
            // Find the role by the isAssignable
            if (type.isAssignable(entry.getKey())) {
                return entry.getValue();
            }
        }
        for (Map.Entry<String, String> e : COMMON_ANNOTATION_ROLES) {
            if (type.hasStereotype(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    @Nullable
    private String findAnnotationRole(Element element) {
        for (Map.Entry<String, String> e : COMMON_ANNOTATION_ROLES) {
            if (element.hasStereotype(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private List<ParameterElement> getParametersNotInRole(ParameterElement[] parameters) {
        List<ParameterElement> parametersNotInRole = new ArrayList<>();
        for (ParameterElement parameter : parameters) {
            ClassElement type = parameter.getType();
            if (Objects.requireNonNull(currentClassTypeRoles).keySet().stream().noneMatch(type::isAssignable)) {
                parametersNotInRole.add(parameter);
            }
        }
        return parametersNotInRole;
    }

    private void processMethodInfo(MethodMatchContext methodMatchContext, MethodMatchInfo methodInfo) {
        QueryBuilder queryEncoder = methodMatchContext.getQueryBuilder();
        MethodElement method = methodMatchContext.getMethodElement();

        // populate parameter roles
        for (Map.Entry<Element, String> entry : methodMatchContext.getParametersInRole().entrySet()) {
            methodInfo.addParameterRole(
                (ParameterElement) entry.getKey(),
                entry.getValue()
            );
        }

        List<QueryParameterBinding> parameterBinding;
        QueryResult queryResult = methodInfo.getQueryResult();
        if (queryResult == null) {
            parameterBinding = List.of();
        } else {
            parameterBinding = queryResult.getParameterBindings();

            if (methodInfo.isRawQuery()) {

                method.annotate(Query.class, (builder) -> builder.member(DataMethod.META_MEMBER_RAW_QUERY,
                    method.stringValue(Query.class)
                        .map(q -> addRawQueryParameterPlaceholders(queryEncoder, queryResult.getQuery(), queryResult.getQueryParts()))
                        .orElse(null)));

                ClassElement genericReturnType = methodMatchContext.getReturnType();
                if (methodMatchContext.isTypeInRole(genericReturnType, TypeRole.PAGE)
                    || methodMatchContext.isTypeInRole(genericReturnType, TypeRole.CURSORED_PAGE)
                    || method.isPresent(Query.class, "countQuery")
                ) {
                    QueryResult countQueryResult = methodInfo.getCountQueryResult();
                    if (countQueryResult == null) {
                        throw new ProcessingException(method, "Query returns a Page and does not specify a 'countQuery' member.");
                    } else {
                        method.annotate(
                            Query.class,
                            (builder) -> builder.member(DataMethod.META_MEMBER_RAW_COUNT_QUERY, addRawQueryParameterPlaceholders(queryEncoder, countQueryResult.getQuery(), countQueryResult.getQueryParts()))
                        );
                    }
                }

            } else {

                bindAdditionalParameters(methodMatchContext, parameterBinding, queryResult.getAdditionalRequiredParameters());

                QueryResult preparedCount = methodInfo.getCountQueryResult();
                if (preparedCount != null) {
                    method.annotate(Query.class, annotationBuilder -> {
                            annotationBuilder.value(queryResult.getQuery());
                            annotationBuilder.member(DataMethod.META_MEMBER_COUNT_QUERY, preparedCount.getQuery());
                        }
                    );
                } else {
                    method.annotate(Query.class, annotationBuilder -> {
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
                    method.removeAnnotation(Join.class);
                    joinPaths.forEach(joinPath -> method.annotate(Join.class, builder -> {
                        builder.member("value", joinPath.getPath())
                            .member("type", joinPath.getJoinType());
                        if (joinPath.getAlias().isPresent()) {
                            builder.member("alias", joinPath.getAlias().get());
                        }
                    }));
                }
            }
        }

        annotateQueryResultIfApplicable(method, methodInfo, methodMatchContext.getRootEntity());

        method.annotate(DataMethod.class.getName(), annotationBuilder -> {

            ClassElement runtimeInterceptor = methodInfo.getRuntimeInterceptor();
            if (runtimeInterceptor == null) {
                throw new MatchFailedException("Unable to implement Repository method: " + methodMatchContext.getRepositoryClass().getSimpleName() + "." + method.getName() + "(..). No possible runtime implementations found.", method);
            }
            annotationBuilder.member(DataMethod.META_MEMBER_INTERCEPTOR, new AnnotationClassValue<>(runtimeInterceptor.getName()));
            if (methodMatchContext.getRootEntity() != null) {
                annotationBuilder.member(DataMethod.META_MEMBER_ROOT_ENTITY, new AnnotationClassValue<>(methodMatchContext.getRootEntity().getName()));
            }

            if (methodInfo.isDto()) {
                annotationBuilder.member(DataMethod.META_MEMBER_DTO, true);
            }
            if (methodInfo.isOptimisticLock()) {
                annotationBuilder.member(DataMethod.META_MEMBER_OPTIMISTIC_LOCK, true);
            }

            if (!methodInfo.getParameterRoles().isEmpty()) {
                // include the roles
                for (Map.Entry<ParameterElement, String> e : methodInfo.getParameterRoles().entrySet()) {
                    // Legacy parameters binding doesn't allow duplicate roles
                    ParameterElement parameter = e.getKey();
                    annotationBuilder.member(e.getValue(), parameter.stringValue(Parameter.class).orElse(parameter.getName()));
                }

                List<ParameterElement> parameters = List.of(method.getParameters());
                annotationBuilder.member(DataMethodQuery.META_MEMBER_PARAMETERS_TYPE_ROLES,
                    methodInfo.getParameterRoles().entrySet()
                        .stream()
                        .sorted(Comparator.comparingInt(o -> parameters.indexOf(o.getKey())))
                        .map(e ->
                            AnnotationValue.builder("type")
                                .value(e.getValue())
                                .member("parameterIndex", parameters.indexOf(e.getKey()))
                                .build()
                        ).toArray(AnnotationValue[]::new)
                );
            }

            String returnTypeRole = findTypeRole(method.getReturnType().getType());
            if (returnTypeRole == null) {
                returnTypeRole =  findAnnotationRole(method.getReturnType());
            }
            if (returnTypeRole != null) {
                annotationBuilder.member(DataMethodQuery.META_MEMBER_RETURN_TYPE_ROLE, returnTypeRole);
            }

            addQueryDefinition(methodMatchContext,
                annotationBuilder,
                methodInfo.getOperationType(),
                queryResult,
                methodInfo.getResultType(),
                parameterBinding,
                methodInfo.isEncodeEntityParameters(),
                methodInfo.isOptimisticLock());

            List<AnnotationValue<Annotation>> additionalQueryAnnotations = new ArrayList<>(methodInfo.getAdditionalQueries().size());
            for (MethodMatchInfo.QueryDefinition queryDefinition : methodInfo.getAdditionalQueries()) {
                QueryResult additionalQueryResult = queryDefinition.queryResult();
                List<QueryParameterBinding> additionalParameterBinding = additionalQueryResult.getParameterBindings();
                bindAdditionalParameters(methodMatchContext, additionalParameterBinding, additionalQueryResult.getAdditionalRequiredParameters());

                AnnotationValueBuilder<Annotation> builder = AnnotationValue.builder(DataMethodQuery.class.getName());
                String query = additionalQueryResult.getQuery();
                if (methodInfo.isRawQuery()) {
                    query = addRawQueryParameterPlaceholders(queryEncoder, query, additionalQueryResult.getQueryParts());
                }
                builder.member(AnnotationMetadata.VALUE_MEMBER, query);
                builder.member(DataMethodQuery.META_MEMBER_NATIVE, method.booleanValue(Query.class,
                    DataMethodQuery.META_MEMBER_NATIVE).orElse(false));

                addQueryDefinition(methodMatchContext,
                    builder,
                    queryDefinition.operationType(),
                    additionalQueryResult,
                    queryDefinition.resultType(),
                    additionalParameterBinding,
                    methodInfo.isEncodeEntityParameters(),
                    queryDefinition.optimisticLock());

                additionalQueryAnnotations.add(builder.build());
            }
            if (!additionalQueryAnnotations.isEmpty()) {
                annotationBuilder.member(DataMethod.META_MEMBER_QUERIES, additionalQueryAnnotations.toArray(AnnotationValue[]::new));
            }

            QueryResult countQuery = methodInfo.getCountQueryResult();
            if (countQuery != null) {
                List<QueryParameterBinding> countParametersBindings = countQuery.getParameterBindings();
                bindAdditionalParameters(methodMatchContext, countParametersBindings, countQuery.getAdditionalRequiredParameters());

                AnnotationValueBuilder<Annotation> builder = AnnotationValue.builder(DataMethodQuery.class.getName());

                String query = countQuery.getQuery();
                if (methodInfo.isRawQuery()) {
                    query = addRawQueryParameterPlaceholders(queryEncoder, query, countQuery.getQueryParts());
                }

                builder.member(AnnotationMetadata.VALUE_MEMBER, query);
                builder.member(DataMethodQuery.META_MEMBER_NATIVE, method.booleanValue(Query.class,
                    DataMethodQuery.META_MEMBER_NATIVE).orElse(false));

                addQueryDefinition(methodMatchContext,
                    builder,
                    DataMethod.OperationType.COUNT,
                    countQuery,
                    methodMatchContext.getVisitorContext().getClassElement(Long.class).orElseThrow(),
                    countParametersBindings,
                    methodInfo.isEncodeEntityParameters(),
                    false);

                annotationBuilder.member(DataMethod.META_MEMBER_COUNT_QUERY, builder.build());
            }
        });
    }

    private void addQueryDefinition(MethodMatchContext methodMatchContext,
                                    AnnotationValueBuilder<Annotation> annotationBuilder,
                                    DataMethod.OperationType operationType,
                                    @Nullable
                                    QueryResult queryResult,
                                    @Nullable
                                    TypedElement resultType,
                                    List<QueryParameterBinding> parameterBinding,
                                    boolean encodeEntityParameters,
                                    boolean optimisticLock) {

        if (methodMatchContext.getMethodElement().hasAnnotation(Procedure.class)) {
            annotationBuilder.member(DataMethodQuery.META_MEMBER_PROCEDURE, true);
        }

        annotationBuilder.member(DataMethodQuery.META_MEMBER_OPERATION_TYPE, operationType);
        if (optimisticLock) {
            annotationBuilder.member(DataMethodQuery.META_MEMBER_OPTIMISTIC_LOCK, true);
        }

        if (resultType != null) {
            String stringType = resultType.getName();
            if (resultType.isArray() && !stringType.endsWith("[]")) {
                stringType += "[]";
            }
            annotationBuilder.member(DataMethodQuery.META_MEMBER_RESULT_TYPE, new AnnotationClassValue<>(stringType));
            ClassElement type = resultType.getType();
            if (!TypeUtils.isVoid(type)) {
                annotationBuilder.member(DataMethodQuery.META_MEMBER_RESULT_DATA_TYPE, TypeUtils.resolveDataType(type, dataTypes));
            }
        }

        if (queryResult != null) {
            if (parameterBinding.stream().anyMatch(QueryParameterBinding::isExpandable)) {
                annotationBuilder.member(DataMethodQuery.META_MEMBER_EXPANDABLE_QUERY, queryResult.getQueryParts().toArray(new String[0]));
            }
            // OUT parameter bindings (e.g. Oracle RETURNING ... INTO ...)
            List<QueryOutParameterBinding> outBindings = queryResult.getOutParameterBindings();
            if (CollectionUtils.isNotEmpty(outBindings)) {
                List<AnnotationValue<?>> outAnnotations = new ArrayList<>(outBindings.size());
                for (QueryOutParameterBinding b : outBindings) {
                    AnnotationValueBuilder<?> outBuilder = AnnotationValue.builder(DataMethodQueryOutParameter.class);
                    outBuilder.member(DataMethodQueryOutParameter.META_MEMBER_NAME, b.getName());
                    outBuilder.member(DataMethodQueryOutParameter.META_MEMBER_DATA_TYPE, b.getDataType());
                    outAnnotations.add(outBuilder.build());
                }
                annotationBuilder.member(DataMethodQuery.META_MEMBER_OUT_PARAMETERS, outAnnotations.toArray(new AnnotationValue[0]));
            }

            int max = queryResult.getMax();
            if (max > -1) {
                annotationBuilder.member(DataMethodQuery.META_MEMBER_LIMIT, max);
            }
            long offset = queryResult.getOffset();
            if (offset > 0) {
                annotationBuilder.member(DataMethodQuery.META_MEMBER_OFFSET, offset);
            }
            Sort sort = queryResult.getSort();
            if (sort.isSorted()) {
                annotationBuilder.member(DataMethodQuery.META_MEMBER_SORT, sort.getOrderBy().stream().map(order ->
                        AnnotationValue.builder("order") // ?? Should we add a new annotation
                            .value(order.getProperty())
                            .member("direction", order.getDirection())
                            .member("ignoreCase", order.isIgnoreCase())
                            .build())
                    .toArray(AnnotationValue[]::new)
                );
            }
        }

        if (CollectionUtils.isNotEmpty(parameterBinding)) {
            bindParameters(
                methodMatchContext.supportsImplicitQueries(),
                parameterBinding,
                encodeEntityParameters,
                annotationBuilder
            );
        }
    }

    private void bindParameters(boolean supportsImplicitQueries,
                                List<QueryParameterBinding> parameterBinding,
                                boolean finalEncodeEntityParameters,
                                AnnotationValueBuilder<Annotation> annotationBuilder) {

        List<AnnotationValue<?>> annotationValues = new ArrayList<>(parameterBinding.size());
        for (QueryParameterBinding p : parameterBinding) {
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
            builder.member(DataMethodQueryParameter.META_MEMBER_JSON_DATA_TYPE, p.getJsonDataType());
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
            if (p.isExpression()) {
                builder.member(DataMethodQueryParameter.META_MEMBER_EXPRESSION, true);
                if (!supportsImplicitQueries) {
                    builder.member(DataMethodQueryParameter.META_MEMBER_NAME, p.getName());
                }
                Object value = p.getValue();
                if (value != null) {
                    if (value instanceof String expression) {
                        // TODO: Support adding an expression annotation value in Core
                        String originatingClassName = DataMethodQueryParameter.class.getName();
                        String packageName = NameUtils.getPackageName(originatingClassName);
                        String simpleClassName = NameUtils.getSimpleName(originatingClassName);
                        String exprClassName = "%s.$%s%s".formatted(packageName, simpleClassName, EvaluatedExpressionReferenceCounter.EXPR_SUFFIX);

                        Integer expressionIndex = EvaluatedExpressionReferenceCounter.nextIndex(exprClassName);

                        builder.members(Map.of(
                            AnnotationMetadata.VALUE_MEMBER,
                            new EvaluatedExpressionReference(expression, originatingClassName, AnnotationMetadata.VALUE_MEMBER, exprClassName + expressionIndex)
                        ));
                    } else {
                        throw new IllegalStateException("The expression value should be a String!");
                    }
                }
            }
            if (supportsImplicitQueries) {
                builder.member(DataMethodQueryParameter.META_MEMBER_NAME, p.getKey());
            }
            if (p.getRole() != null) {
                builder.member(DataMethodQueryParameter.META_MEMBER_ROLE, p.getRole());
            }
            if (p.getTableAlias() != null) {
                builder.member(DataMethodQueryParameter.META_MEMBER_TABLE_ALIAS, p.getTableAlias());
            }
            annotationValues.add(builder.build());
        }
        AnnotationValue[] annotations = annotationValues.toArray(new AnnotationValue[0]);
        annotationBuilder.member(DataMethod.META_MEMBER_PARAMETERS, annotations);
    }

    private void bindAdditionalParameters(MethodMatchContext methodMatchContext,
                                          List<QueryParameterBinding> parameterBinding,
                                          Map<String, String> params) {
        SourcePersistentEntity entity = methodMatchContext.getRootEntity();
        Objects.requireNonNull(entity);
        ParameterElement[] parameters = methodMatchContext.getParameters();

        Map<String, DataType> configuredDataTypes = Utils.getConfiguredDataTypes(methodMatchContext.getRepositoryClass());

        for (ListIterator<QueryParameterBinding> iterator = parameterBinding.listIterator(); iterator.hasNext(); ) {
            QueryParameterBinding queryParameterBinding = iterator.next();
            if (queryParameterBinding instanceof AdditionalParameterBinding additionalParameterBinding) {
                iterator.set(
                    createAdditionalBinding(
                        additionalParameterBinding.bindingContext(),
                        methodMatchContext,
                        entity,
                        parameters,
                        Objects.requireNonNull(additionalParameterBinding.getName()),
                        configuredDataTypes
                    )
                );
            }
        }

        if (CollectionUtils.isNotEmpty(params)) {
            for (Map.Entry<String, String> param : params.entrySet()) {
                String key = param.getKey();
                String name = param.getValue();

                parameterBinding.add(
                    createAdditionalBinding(
                        BindingParameter.BindingContext.create().name(key),
                        methodMatchContext,
                        entity,
                        parameters,
                        name,
                        configuredDataTypes
                    )
                );

            }
        }
    }

    private QueryParameterBinding createAdditionalBinding(BindingParameter.BindingContext bindingContext,
                                                          MatchContext matchContext,
                                                          SourcePersistentEntity entity,
                                                          ParameterElement[] parameters,
                                                          String name,
                                                          Map<String, DataType> configuredDataTypes) {

        List<AnnotationValue<ParameterExpression>> parameterExpressions = matchContext.getMethodElement()
            .getAnnotationMetadata()
            .getAnnotationValuesByType(ParameterExpression.class);

        Optional<AnnotationValue<ParameterExpression>> parameterExpression = parameterExpressions.stream()
            .filter(av -> av.stringValue("name").orElse("").equals(name))
            .findFirst();

        if (parameterExpression.isPresent()) {
            ClassElement type = RawQueryMethodMatcher.extractExpressionType(matchContext, parameterExpression.orElseThrow());
            return new SourceParameterExpressionImpl(configuredDataTypes, name, type, null)
                .bind(bindingContext);
        }

        ParameterElement parameter = Arrays.stream(parameters)
            .filter(p -> p.stringValue(Parameter.class).orElse(p.getName()).equals(name))
            .findFirst().orElse(null);

        if (parameter == null) {
            throw new MatchFailedException("A @Where(..) definition requires a parameter called [" + name + "] which is not present in the method signature.");
        }

        PersistentPropertyPath propertyPath = entity.getPropertyPath(name);

        bindingContext = bindingContext.incomingMethodParameterProperty(propertyPath)
            .outgoingQueryParameterProperty(propertyPath);

        return new SourceParameterExpressionImpl(configuredDataTypes,
            matchContext.parameters,
            parameter,
            false,
            null)
            .bind(bindingContext);
    }

    private String addRawQueryParameterPlaceholders(QueryBuilder queryEncoder, String query, List<String> queryParts) {
        if (queryEncoder instanceof SqlQueryBuilder sqlQueryBuilder) {
            Iterator<String> iterator = queryParts.iterator();
            String first = iterator.next();
            if (queryParts.size() < 2) {
                return first;
            }
            var sb = new StringBuilder(first);
            int i = 1;
            while (iterator.hasNext()) {
                sb.append(sqlQueryBuilder.formatParameter(i++).name());
                sb.append(iterator.next());
            }
            return sb.toString();
        }
        return query;
    }

    @Nullable
    private SourcePersistentEntity resolvePersistentEntity(ClassElement repositoryClass,
                                                           MethodElement element,
                                                           Map<Element, String> parametersInRole,
                                                           List<ParameterElement> parametersNotInRole,
                                                           Function<ClassElement, SourcePersistentEntity> entityResolver) {
        ClassElement returnType = element.getGenericReturnType();
        SourcePersistentEntity entity = resolveEntityForCurrentClass(repositoryClass, entityResolver);
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
                    parametersInRole.put(((SourcePersistentProperty) persistentProperty).getPropertyElement(), role);
                }
            }
            return entity;
        }
        SourcePersistentEntity sourcePersistentEntity = resolvePersistentEntityFromLifecycleMethods(element, parametersNotInRole, entityResolver);
        if (sourcePersistentEntity != null) {
            return sourcePersistentEntity;
        }
        if (element.hasStereotype(Query.class)) {
            return null;
        }
        ClassElement owningType = element.getOwningType();
        for (MethodElement method : owningType.getMethods()) {
            return resolvePersistentEntityFromLifecycleMethods(method, getParametersNotInRole(method.getParameters()), entityResolver);
        }
        throw new MatchFailedException("Could not resolved root entity. Either implement the Repository interface or define the entity as part of the signature", element);
    }

    @Nullable
    private SourcePersistentEntity resolvePersistentEntityFromLifecycleMethods(MethodElement element,
                                                                               List<ParameterElement> parametersNotInRole,
                                                                               Function<ClassElement, SourcePersistentEntity> entityResolver) {
        if (element.hasStereotype(Insert.class) || element.hasStereotype(Update.class) || element.hasStereotype(Delete.class)) {
            if (!parametersNotInRole.isEmpty()) {
                ClassElement type = parametersNotInRole.iterator().next().getGenericType();
                if (type.isArray()) {
                    type = type.fromArray();
                } else if (type.isAssignable(Iterable.class)) {
                    type = type.getTypeArguments(Iterable.class).entrySet().iterator().next().getValue();
                }
                if (type.hasStereotype(MappedEntity.class)) {
                    return entityResolver.apply(type);
                }
            }
        }
        return null;
    }

    @Nullable
    private SourcePersistentEntity resolveEntityForCurrentClass(ClassElement repositoryClass, Function<ClassElement, SourcePersistentEntity> entityResolver) {
        SourcePersistentEntity entity = resolveEntityForCurrentClass(repositoryClass, entityResolver, GenericRepository.class, "E");
        if (entity != null) {
            return entity;
        }
        entity = resolveEntityForCurrentClass(repositoryClass, entityResolver, SPRING_REPO, "T");
        if (entity != null) {
            return entity;
        }
        entity = resolveEntityForCurrentClass(repositoryClass, entityResolver, JAKARTA_DATA_REPO, "T");
        if (entity != null) {
            return entity;
        }
        entity = resolveEntityForCurrentClass(repositoryClass, entityResolver, JPA_SPECIFICATION_EXECUTOR, "T");
        if (entity != null) {
            return entity;
        }
        entity = resolveEntityForCurrentClass(repositoryClass, entityResolver, ASYNC_JPA_SPECIFICATION_EXECUTOR, "T");
        if (entity != null) {
            return entity;
        }
        entity = resolveEntityForCurrentClass(repositoryClass, entityResolver, REACTIVE_STREAMS_JPA_SPECIFICATION_EXECUTOR, "T");
        if (entity != null) {
            return entity;
        }
        return resolveEntityForCurrentClass(repositoryClass, entityResolver, REACTOR_JPA_SPECIFICATION_EXECUTOR, "T");
    }

    @Nullable
    private SourcePersistentEntity resolveEntityForCurrentClass(ClassElement repositoryClass,
                                                               Function<ClassElement, SourcePersistentEntity> entityResolver,
                                                               Class<?> repositoryType,
                                                               String argName) {
        return resolveEntityFromTypeArguments(repositoryClass.getTypeArguments(repositoryType), entityResolver, argName);
    }

    @Nullable
    private SourcePersistentEntity resolveEntityForCurrentClass(ClassElement repositoryClass,
                                                               Function<ClassElement, SourcePersistentEntity> entityResolver,
                                                               String repositoryType,
                                                               String argName) {
        return resolveEntityFromTypeArguments(repositoryClass.getTypeArguments(repositoryType), entityResolver, argName);
    }

    @Nullable
    private SourcePersistentEntity resolveEntityFromTypeArguments(Map<String, ClassElement> typeArguments,
                                                                  Function<ClassElement, SourcePersistentEntity> entityResolver,
                                                                  String argName) {
        if (typeArguments.isEmpty()) {
            return null;
        }
        ClassElement classElement = typeArguments.get(argName);
        if (classElement == null) {
            classElement = typeArguments.values().iterator().next();
        }
        if (classElement == null) {
            return null;
        }
        return entityResolver.apply(classElement);
    }

    private void annotateEntityRepresentationIfPresent(ClassElement repositoryClass, Function<ClassElement, SourcePersistentEntity> entityResolver) {
        SourcePersistentEntity entity = resolveEntityForCurrentClass(repositoryClass, entityResolver);
        if (entity != null) {
            AnnotationValue<EntityRepresentation> entityRepresentationAnnotationValue = entity.getAnnotation(EntityRepresentation.class);
            if (entityRepresentationAnnotationValue != null) {
                repositoryClass.annotate(entityRepresentationAnnotationValue);
            }
        }
    }

    /**
     * Annotates method element with {@link io.micronaut.data.annotation.QueryResult} if root entity is {@link EntityRepresentation} of JSON type
     * and method is {@link DataMethod.OperationType#QUERY}.
     *
     * @param element    the method element
     * @param methodInfo the method match info
     * @param entity     the root entity
     */
    private void annotateQueryResultIfApplicable(MethodElement element, MethodMatchInfo methodInfo, SourcePersistentEntity entity) {
        TypedElement resultType = methodInfo.getResultType();
        if (methodInfo.getOperationType() == DataMethod.OperationType.QUERY && resultType != null && resultType.equals(entity.getType())) {
            AnnotationValue<EntityRepresentation> entityRepresentationAnnotationValue = entity.getAnnotation(EntityRepresentation.class);
            if (entityRepresentationAnnotationValue != null) {
                EntityRepresentation.Type type = entityRepresentationAnnotationValue.getRequiredValue("type", EntityRepresentation.Type.class);
                String column = entityRepresentationAnnotationValue.getRequiredValue("column", String.class);
                JsonDataType jsonDataType = JsonDataType.DEFAULT;
                io.micronaut.data.annotation.QueryResult.Type queryResultType = type == EntityRepresentation.Type.TABULAR ? io.micronaut.data.annotation.QueryResult.Type.TABULAR : io.micronaut.data.annotation.QueryResult.Type.JSON;
                element.annotate(io.micronaut.data.annotation.QueryResult.class, builder -> builder
                    .member("type", queryResultType)
                    .member("jsonDataType", jsonDataType)
                    .member("column", column));
            }
        }
    }

}
