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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.DataAnnotationUtils;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.QueryHint;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.annotation.repeatable.QueryHints;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityFrom;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Abstract criteria matcher.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public abstract class AbstractCriteriaMethodMatch implements MethodMatcher.MethodMatch {

    private static final String OPERATOR_OR = "Or";
    private static final String OPERATOR_AND = "And";
    private static final String[] OPERATORS = {OPERATOR_AND, OPERATOR_OR};
    private static final String NOT = "Not";
    private static final String IGNORE_CASE = "IgnoreCase";

    private static final Map<String, Pattern> OPERATOR_PATTERNS;

    private static final List<String> PROPERTY_RESTRICTIONS;
    private static final Pattern RESTRICTIONS_PATTERN;

    static {
        OPERATOR_PATTERNS = new TreeMap<>();
        for (String operator : OPERATORS) {
            OPERATOR_PATTERNS.put(operator, Pattern.compile("(\\w+)(" + operator + ")(\\p{Upper})(\\w+)"));
        }
        PROPERTY_RESTRICTIONS = Restrictions.PROPERTY_RESTRICTIONS_MAP.keySet()
            .stream()
            .sorted(Comparator.comparingInt(String::length).thenComparing(String.CASE_INSENSITIVE_ORDER).reversed())
            .collect(Collectors.toList());
        List<String> restrictionElements = new ArrayList<>(Restrictions.RESTRICTIONS_MAP.keySet());
        restrictionElements.sort(Comparator.comparingInt(String::length).thenComparing(String.CASE_INSENSITIVE_ORDER).reversed());
        String rExpressionPattern = String.join("|", restrictionElements);
        RESTRICTIONS_PATTERN = Pattern.compile("^(" + rExpressionPattern + ")$");
    }

    @Nullable
    protected final List<MethodNameParser.Match> matches;

    protected AbstractCriteriaMethodMatch(List<MethodNameParser.Match> matches) {
        this.matches = matches;
    }

    /**
     * @return The entity parameter
     */
    @Nullable
    protected ParameterElement getEntityParameter() {
        return null;
    }

    /**
     * @return The entities parameter
     */
    @Nullable
    protected ParameterElement getEntitiesParameter() {
        return null;
    }

    /**
     * @return The operation type
     */
    @NonNull
    protected abstract DataMethod.OperationType getOperationType();

    /**
     * @return true of the operation is supported by implicit queries
     */
    protected boolean supportedByImplicitQueries() {
        return false;
    }

    @Override
    public final MethodMatchInfo buildMatchInfo(MethodMatchContext matchContext) {
        MethodMatchInfo methodMatchInfo;
        if (supportedByImplicitQueries() && matchContext.supportsImplicitQueries() && hasNoWhereAndJoinDeclaration(matchContext)) {
            FindersUtils.InterceptorMatch entry = resolveReturnTypeAndInterceptor(matchContext);
            methodMatchInfo = new MethodMatchInfo(
                    getOperationType(),
                    entry.returnType(),
                    entry.interceptor()
            );
        } else {
            methodMatchInfo = build(matchContext);
        }

        ParameterElement entityParameter = getEntityParameter();
        ParameterElement entitiesParameter = getEntitiesParameter();
        ParameterElement idParameter = Arrays.stream(matchContext.getParameters()).filter(p -> p.hasAnnotation(Id.class)).findFirst().orElse(null);
        if (idParameter != null) {
            methodMatchInfo.addParameterRole(TypeRole.ID, idParameter.stringValue(Parameter.class).orElse(idParameter.getName()));
        }
        boolean encodeEntityParameters = !DataAnnotationUtils.hasJsonEntityRepresentationAnnotation(matchContext.getAnnotationMetadata());
        if (entityParameter != null) {
            methodMatchInfo.encodeEntityParameters(encodeEntityParameters);
            methodMatchInfo.addParameterRole(TypeRole.ENTITY, entityParameter.getName());
        } else if (entitiesParameter != null) {
            methodMatchInfo.encodeEntityParameters(encodeEntityParameters);
            methodMatchInfo.addParameterRole(TypeRole.ENTITIES, entitiesParameter.getName());
        }
        return methodMatchInfo;
    }

    protected abstract MethodMatchInfo build(MethodMatchContext matchContext);

    /**
     * @param matchContext    The match context
     * @param interceptorType The interceptor type
     * @return The resolved class element
     */
    protected final ClassElement getInterceptorElement(MethodMatchContext matchContext, Class<? extends DataInterceptor> interceptorType) {
        return FindersUtils.getInterceptorElement(matchContext, interceptorType);
    }

    /**
     * @param matchContext The match context
     * @return resolved return type and interceptor
     */
    protected FindersUtils.InterceptorMatch resolveReturnTypeAndInterceptor(MethodMatchContext matchContext) {
        ParameterElement entityParameter = getEntityParameter();
        ParameterElement entitiesParameter = getEntitiesParameter();

        return FindersUtils.resolveInterceptorTypeByOperationType(
                entityParameter != null,
                entitiesParameter != null,
                getOperationType(),
                matchContext);
    }

    @Nullable
    private Predicate extractPredicates(List<ParameterElement> queryParams,
                                        PersistentEntityRoot<?> root,
                                        SourcePersistentEntityCriteriaBuilder cb) {
        if (CollectionUtils.isNotEmpty(queryParams)) {
            PersistentEntity rootEntity = root.getPersistentEntity();
            List<Predicate> predicates = new ArrayList<>(queryParams.size());
            for (ParameterElement queryParam : queryParams) {
                String paramName = queryParam.getName();
                PersistentPropertyPath propPath = rootEntity.getPropertyPath(rootEntity.getPath(paramName).orElse(paramName));
                ParameterExpression<Object> param = cb.parameter(queryParam);
                if (propPath == null) {
                    if (TypeRole.ID.equals(paramName) && (rootEntity.hasIdentity() || rootEntity.hasCompositeIdentity())) {
                        predicates.add(cb.equal(root.id(), param));
                    } else {
                        throw new MatchFailedException("Cannot query persistentEntity [" + rootEntity.getSimpleName() + "] on non-existent property: " + paramName);
                    }
                } else {
                    PersistentProperty property = propPath.getProperty();
                    if (property == rootEntity.getIdentity()) {
                        predicates.add(cb.equal(root.id(), param));
                    } else if (property == rootEntity.getVersion()) {
                        predicates.add(cb.equal(root.version(), param));
                    } else {
                        if (propPath.getAssociations().isEmpty()) {
                            predicates.add(cb.equal(root.get(property.getName()), param));
                        } else {
                            // TODO: support embedded ID
                            Association association = propPath.getAssociations().get(0);
                            if (propPath.getAssociations().size() == 1 && PersistentEntityUtils.isAccessibleWithoutJoin(association, property)) {
                                predicates.add(cb.equal(root.join(association.getName()).get(property.getName()), param));
                            } else {
                                throw new MatchFailedException("Cannot apply a predicate to a path with an association: " + paramName);
                            }
                        }
                    }
                }
            }
            if (predicates.isEmpty()) {
                return null;
            }
            return cb.and(predicates);
        }
        return null;
    }

    /**
     * Apply predicates.
     *
     * @param querySequence The query sequence
     * @param parameters    The parameters
     * @param root          The root
     * @param query         The query
     * @param cb            The criteria builder
     * @param <T>           The entity type
     */
    protected <T> void applyPredicates(String querySequence,
                                       ParameterElement[] parameters,
                                       PersistentEntityRoot<T> root,
                                       PersistentEntityCriteriaQuery<T> query,
                                       SourcePersistentEntityCriteriaBuilder cb) {
        Predicate predicate = extractPredicates(querySequence, Arrays.asList(parameters).iterator(), root, cb);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    /**
     * Apply predicates.
     *
     * @param querySequence The query sequence
     * @param parameters    The parameters
     * @param root          The root
     * @param query         The query
     * @param cb            The criteria builder
     * @param <T>           The entity type
     */
    protected <T> void applyPredicates(String querySequence,
                                       ParameterElement[] parameters,
                                       PersistentEntityRoot<T> root,
                                       PersistentEntityCriteriaDelete<T> query,
                                       SourcePersistentEntityCriteriaBuilder cb) {
        Predicate predicate = extractPredicates(querySequence, Arrays.asList(parameters).iterator(), root, cb);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    /**
     * Apply predicates.
     *
     * @param querySequence The query sequence
     * @param parameters    The parameters
     * @param root          The root
     * @param query         The query
     * @param cb            The criteria builder
     * @param <T>           The entity type
     */
    protected <T> void applyPredicates(String querySequence,
                                       ParameterElement[] parameters,
                                       PersistentEntityRoot<T> root,
                                       PersistentEntityCriteriaUpdate<T> query,
                                       SourcePersistentEntityCriteriaBuilder cb) {
        Predicate predicate = extractPredicates(querySequence, Arrays.asList(parameters).iterator(), root, cb);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    /**
     * Apply predicates based on parameters.
     *
     * @param parameters The parameters
     * @param root       The root
     * @param query      The query
     * @param cb         The criteria builder
     * @param <T>        The entity type
     */
    protected <T> void applyPredicates(List<ParameterElement> parameters,
                                       PersistentEntityRoot<T> root,
                                       PersistentEntityCriteriaQuery<T> query,
                                       SourcePersistentEntityCriteriaBuilder cb) {
        Predicate predicate = extractPredicates(parameters, root, cb);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    /**
     * Apply predicates based on parameters.
     *
     * @param parameters The parameters
     * @param root       The root
     * @param query      The query
     * @param cb         The criteria builder
     * @param <T>        The entity type
     */
    protected <T> void applyPredicates(List<ParameterElement> parameters,
                                       PersistentEntityRoot<T> root,
                                       PersistentEntityCriteriaUpdate<T> query,
                                       SourcePersistentEntityCriteriaBuilder cb) {
        Predicate predicate = extractPredicates(parameters, root, cb);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    /**
     * Apply predicates based on parameters.
     *
     * @param parameters The parameters
     * @param root       The root
     * @param query      The query
     * @param cb         The criteria builder
     * @param <T>        The entity type
     */
    protected <T> void applyPredicates(List<ParameterElement> parameters,
                                       PersistentEntityRoot<T> root,
                                       PersistentEntityCriteriaDelete<T> query,
                                       SourcePersistentEntityCriteriaBuilder cb) {
        Predicate predicate = extractPredicates(parameters, root, cb);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    private <T> Predicate extractPredicates(String querySequence,
                                            Iterator<ParameterElement> parametersIt,
                                            PersistentEntityRoot<T> root,
                                            SourcePersistentEntityCriteriaBuilder cb) {
        Predicate predicate = null;

        // if it contains operator and split
        boolean containsOperator = false;
        if (querySequence != null) {
            List<Predicate> predicates = new ArrayList<>();
            for (Map.Entry<String, Pattern> operatorPatternEntry : OPERATOR_PATTERNS.entrySet()) {
                Matcher currentMatcher = operatorPatternEntry.getValue().matcher(querySequence);
                if (currentMatcher.find()) {
                    containsOperator = true;
                    String operatorInUse = operatorPatternEntry.getKey();

                    String[] queryParameters = querySequence.split(operatorInUse);
                    List<Predicate> opPredicates = new ArrayList<>();
                    Pattern orPattern = OPERATOR_PATTERNS.get(OPERATOR_OR);
                    for (String queryParameter : queryParameters) {
                        // Since split was done first by And operator we may have queryParameters with Or predicate
                        // If queryParameters is actual Or expression we need to further extract predicates
                        // And not try to find actual method predicate from the property (queryParameter) containing Or expression
                        if (!OPERATOR_OR.equals(operatorInUse) && orPattern.matcher(queryParameter).find()) {
                            opPredicates.add(extractPredicates(queryParameter, parametersIt, root, cb));
                        } else {
                            opPredicates.add(
                                findMethodPredicate(queryParameter, root, cb, parametersIt)
                            );
                        }
                    }
                    if (!opPredicates.isEmpty()) {
                        if (OPERATOR_OR.equals(operatorInUse)) {
                            predicates.add(cb.or(opPredicates));
                        } else {
                            predicates.add(cb.and(opPredicates));
                        }
                    }
                    break;
                }
            }
            if (!predicates.isEmpty()) {
                predicate = cb.and(predicates);
            }
        }
        if (!containsOperator && querySequence != null) {
            predicate = findMethodPredicate(querySequence, root, cb, parametersIt);
        }
        return predicate;
    }

    private <T> Predicate findMethodPredicate(String expression,
                                              PersistentEntityRoot<T> root,
                                              SourcePersistentEntityCriteriaBuilder cb,
                                              Iterator<ParameterElement> parameters) {
        Optional<String> optionalRestrictionName = PROPERTY_RESTRICTIONS.stream().filter(expression::endsWith).findFirst();
        if (optionalRestrictionName.isPresent()) {
            String restrictionName = optionalRestrictionName.get();
            String propertyName = extractPropertyName(expression, restrictionName);
            if (StringUtils.isEmpty(propertyName)) {
                throw new MatchFailedException("Missing property name for restriction: " + restrictionName);
            }
            if (propertyName.endsWith(IGNORE_CASE)) {
                restrictionName += IGNORE_CASE;
                propertyName = propertyName.substring(IGNORE_CASE.length());
            }
            Restrictions.PropertyRestriction<?> restriction = Restrictions.findPropertyRestriction(restrictionName);
            if (restriction == null) {
                throw new MatchFailedException("Unknown restriction: " + restrictionName);
            }
            return getPropertyRestriction(propertyName, root, cb, parameters, restriction);
        }

        Matcher matcher = RESTRICTIONS_PATTERN.matcher(expression);
        if (matcher.find()) {
            String restrictionName = matcher.group(1);
            Restrictions.Restriction<?> restriction = Restrictions.findRestriction(restrictionName);
            if (restriction == null) {
                throw new MatchFailedException("Unknown restriction: " + restrictionName);
            }
            return getRestriction(root, cb, parameters, restriction);
        }

        String propertyName = expression;
        String restrictionName = "Equals";
        if (propertyName.endsWith(IGNORE_CASE)) {
            restrictionName += IGNORE_CASE;
            propertyName = extractPropertyName(propertyName, IGNORE_CASE);
        }
        Restrictions.PropertyRestriction<?> restriction = Restrictions.findPropertyRestriction(restrictionName);
        return getPropertyRestriction(propertyName, root, cb, parameters, restriction);
    }

    private static String extractPropertyName(String queryParameter, String clause) {
        String propName;
        if (clause != null) {
            int i = queryParameter.lastIndexOf(clause);
            if (i > -1) {
                propName = queryParameter.substring(0, i);
            } else {
                propName = queryParameter;
            }
        } else {
            propName = queryParameter;
        }

        return propName;
    }

    private <T> Predicate getPropertyRestriction(String propertyName,
                                                 PersistentEntityRoot<T> root,
                                                 SourcePersistentEntityCriteriaBuilder cb,
                                                 Iterator<ParameterElement> parameters,
                                                 Restrictions.PropertyRestriction<?> restriction) {
        boolean negation = false;

        if (propertyName.endsWith(NOT)) {
            int i = propertyName.lastIndexOf(NOT);
            propertyName = propertyName.substring(0, i);
            negation = true;
        }

        if (StringUtils.isEmpty(propertyName)) {
            throw new MatchFailedException("No property name specified in clause: " + restriction.getName());
        }

        Expression prop = getProperty(root, propertyName);

        Predicate predicate = restriction.find(root,
                cb,
                prop,
                provideParams(parameters,
                        restriction.getRequiredParameters(),
                        restriction.getName(),
                        cb,
                        prop
                ).toArray(new ParameterExpression[0]));

        if (negation) {
            predicate = predicate.not();
        }
        return predicate;
    }

    private <T> Predicate getRestriction(PersistentEntityRoot<T> root,
                                         SourcePersistentEntityCriteriaBuilder cb,
                                         Iterator<ParameterElement> parameters,
                                         Restrictions.Restriction<?> restriction) {
        Expression<?> property = null;
        if (restriction.getName().equals("Ids")) {
            property = root.id();
        }
        return restriction.find(root,
                cb,
                provideParams(parameters,
                        restriction.getRequiredParameters(),
                        restriction.getName(),
                        cb,
                        property
                ).toArray(new ParameterExpression[0])
        );
    }

    private <T> List<ParameterExpression<T>> provideParams(Iterator<ParameterElement> parameters,
                                                           int requiredParameters,
                                                           String restrictionName,
                                                           SourcePersistentEntityCriteriaBuilder cb,
                                                           @Nullable
                                                           Expression<?> expression) {
        if (requiredParameters == 0) {
            return Collections.emptyList();
        }
        List<ParameterExpression<T>> params = new ArrayList<>(requiredParameters);
        for (int i = 0; i < requiredParameters; i++) {
            if (!parameters.hasNext()) {
                throw new MatchFailedException("Insufficient arguments to method criteria: " + restrictionName);
            }
            ParameterElement parameter = parameters.next();
            ClassElement genericType = parameter.getGenericType();
            if (TypeUtils.isContainerType(genericType)) {
                genericType = genericType.getFirstTypeArgument().orElse(genericType);
            }

            if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> pp) {
                PersistentPropertyPath propertyPath = PersistentPropertyPath.of(pp.getAssociations(), pp.getProperty());
                if (!isValidType(genericType, (SourcePersistentProperty) propertyPath.getProperty())) {
                    SourcePersistentProperty property = (SourcePersistentProperty) propertyPath.getProperty();
                    throw new IllegalArgumentException("Parameter [" + genericType.getType().getName() + " " + parameter.getName() + "] is not compatible with property [" + property.getType().getName() + " " + property.getName() + "] of entity: " + property.getOwner().getName());
                }
            }
            ParameterExpression p = cb.parameter(parameter);
            params.add(p);
        }
        return params;
    }

    private boolean isValidType(ClassElement genericType, SourcePersistentProperty property) {
        if (TypeUtils.isObjectClass(genericType)) {
            // Avoid an error when type information is missing.
            return true;
        }
        PersistentEntity owner = property.getOwner();
        if (TypeUtils.areTypesCompatible(genericType, property.getType()) && !TypeUtils.isObjectClass(genericType)) {
            return true;
        }
        if (TypeUtils.isContainerType(property.getType())) {
            ClassElement genericPropertyType = property.getType().getFirstTypeArgument().orElse(property.getType());
            if (TypeUtils.areTypesCompatible(genericType, genericPropertyType) && !TypeUtils.isObjectClass(genericType)) {
                return true;
            }
        }
        if (owner.hasCompositeIdentity() && property.getOwner().getCompositeIdentity()[0].equals(property)) {
            // Workaround for composite properties
            return true;
        }
        return genericType.isAssignable(Iterable.class);
    }

    @NonNull
    protected final <T> Expression<?> getProperty(PersistentEntityRoot<T> root, String propertyName) {
        if (TypeRole.ID.equals(NameUtils.decapitalize(propertyName)) && (root.getPersistentEntity().hasIdentity() || root.getPersistentEntity().hasCompositeIdentity())) {
            return root.id();
        }
        io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<Object> property = findProperty(root, propertyName);
        if (property != null) {
            return property;
        }
        throw new MatchFailedException("Cannot query entity [" + root.getPersistentEntity().getSimpleName() + "] on non-existent property: " + propertyName);
    }

    @Nullable
    protected final <T> io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<Object> findProperty(PersistentEntityRoot<T> root, String propertyName) {
        propertyName = NameUtils.decapitalize(propertyName);
        PersistentEntity entity = root.getPersistentEntity();
        PersistentProperty prop = entity.getPropertyByName(propertyName);
        PersistentPropertyPath pp;
        if (prop == null) {
            Optional<String> propertyPath = PersistentEntityUtils.getPersistentPropertyPath(entity, propertyName);
            if (propertyPath.isPresent()) {
                String path = propertyPath.get();
                pp = entity.getPropertyPath(path);
                if (pp == null) {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            pp = PersistentPropertyPath.of(Collections.emptyList(), prop, propertyName);
        }

        PersistentEntityFrom<?, ?> path = root;
        for (Association association : pp.getAssociations()) {
            path = path.join(association.getName());
        }
        Path<Object> exp;
        if (pp.getProperty() instanceof Association && ((Association) pp.getProperty()).getKind() != Relation.Kind.EMBEDDED) {
            exp = path.join(pp.getProperty().getName());
        } else {
            exp = path.get(pp.getProperty().getName());
        }
        return CriteriaUtils.requireProperty(exp);
    }

    protected final void applyJoinSpecs(PersistentEntityRoot<?> root, @NonNull List<AnnotationValue<Join>> joinSpecs) {
        for (AnnotationValue<Join> joinSpec : joinSpecs) {
            String path = joinSpec.stringValue().orElse(null);
            Join.Type type = joinSpec.enumValue("type", Join.Type.class).orElse(Join.Type.FETCH);
            String alias = joinSpec.stringValue("alias").orElse(null);
            if (path != null) {
                PersistentPropertyPath propertyPath = root.getPersistentEntity().getPropertyPath(path);
                if (propertyPath == null || !(propertyPath.getProperty() instanceof Association)) {
                    throw new MatchFailedException("Invalid join spec [" + path + "]. Property is not an association!");
                } else {
                    PersistentEntityFrom<?, ?> p = root;
                    for (Association association : propertyPath.getAssociations()) {
                        p = p.join(association.getName(), type);
                    }
                    if (alias != null) {
                        p.join(propertyPath.getProperty().getName(), type, alias);
                    } else {
                        p.join(propertyPath.getProperty().getName(), type);
                    }
                }
            }
        }
    }

    /**
     * @param matchContext The match context
     * @param isQuery      true if is a query criteria
     * @return a List of annotations values for {@link Join} annotation.
     */
    @NonNull
    protected final List<AnnotationValue<Join>> joinSpecsAtMatchContext(@NonNull MethodMatchContext matchContext, boolean isQuery) {
        List<AnnotationValue<Join>> joins;
        if (!isQuery) {
            return matchContext.getAnnotationMetadata().getDeclaredAnnotationValuesByType(Join.class);
        }
        joins = matchContext.getAnnotationMetadata().getAnnotationValuesByType(Join.class);
        if (!joins.isEmpty()) {
            return joins;
        }
        return matchContext.getRepositoryClass().getAnnotationMetadata().getAnnotationValuesByType(Join.class);
    }

    protected final boolean hasNoWhereAndJoinDeclaration(@NonNull MethodMatchContext matchContext) {
        if (matchContext.getMethodElement().hasAnnotation(Join.class)) {
            return false;
        }
        AnnotationMetadataHierarchy metadataHierarchy = new AnnotationMetadataHierarchy(matchContext.getRepositoryClass(), matchContext.getMethodElement());
        if (metadataHierarchy.hasAnnotation("io.micronaut.data.jpa.annotation.EntityGraph")) {
            return false;
        }
        if (metadataHierarchy.hasAnnotation(QueryHint.class) || metadataHierarchy.hasAnnotation(QueryHints.class)) {
            return false;
        }
        final boolean repositoryHasWhere = metadataHierarchy.hasAnnotation(Where.class);
        final boolean entityHasWhere = matchContext.getRootEntity().hasAnnotation(Where.class);
        return !repositoryHasWhere && !entityHasWhere;
    }

    /**
     * Find projection selection.
     *
     * @param projectionPart The projection
     * @param root           The root
     * @param cb             The criteria builder
     * @param returnTypeName The return type name
     * @param <T>            The query type
     * @return The selections
     */
    protected <T> List<Selection<?>> findSelections(String projectionPart,
                                                    PersistentEntityRoot<T> root,
                                                    PersistentEntityCriteriaBuilder cb,
                                                    @Nullable String returnTypeName) {
        if (StringUtils.isEmpty(projectionPart)) {
            return Collections.emptyList();
        }
        List<Selection<?>> selectionList = new ArrayList<>();
        for (String projection : projectionPart.split("And")) {
            io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath = findProperty(root, projection);
            if (propertyPath != null) {
                selectionList.add(propertyPath);
            } else {
                Selection<?> selection = Projections.find(root, cb, projection, this::findProperty);
                if (selection != null) {
                    selectionList.add(selection);
                } else if (!projection.equals(returnTypeName)) {
                    // if the return type simple name is the same then we assume this is ok
                    // this allows for Optional findOptionalByName
                    throw new MatchFailedException("Cannot project on non-existent property: " + NameUtils.decapitalize(projection));
                }
            }
        }
        return selectionList;
    }

    protected final MethodResult analyzeMethodResult(MethodMatchContext matchContext,
                                                     String selectedType,
                                                     ClassElement queryResultType,
                                                     FindersUtils.InterceptorMatch interceptorMatch,
                                                     boolean allowEntityResultByDefault) {
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

        ClassElement resultType = interceptorMatch.returnType();

        boolean isRuntimeDto = false;
        boolean isDto = resultType != null
            && !TypeUtils.areTypesCompatible(resultType, queryResultType)
            && (isDtoType(matchContext.getRepositoryClass(), resultType) || resultType.hasStereotype(Introspected.class) && queryResultType.hasStereotype(MappedEntity.class));

        if (isDto) {
            isRuntimeDto = isDtoType(matchContext.getRepositoryClass(), resultType);
        } else if (interceptorMatch.validateReturnType()) {
            if (resultType == null || (!resultType.isAssignable(void.class) && !resultType.isAssignable(Void.class))) {
                if (resultType == null || TypeUtils.areTypesCompatible(resultType, queryResultType)) {
                    if (!queryResultType.isPrimitive() || resultType == null) {
                        resultType = queryResultType;
                    }
                } else if (!allowEntityResultByDefault || !matchContext.getRootEntity().getClassElement().equals(resultType)) {
                    throw new MatchFailedException("Query results in a type [" + queryResultType.getName() + "] whilst method returns an incompatible type: " + resultType.getName());
                }
            }
        }
        return new MethodResult(resultType, isDto, isRuntimeDto);
    }

    private boolean isDtoType(ClassElement repositoryElement, ClassElement classElement) {
        return Arrays.stream(repositoryElement.stringValues(RepositoryConfiguration.class, "queryDtoTypes"))
            .anyMatch(type -> classElement.getName().equals(type));
    }

    /**
     * Find DTO properties.
     *
     * @param entity     The entity
     * @param returnType The result
     * @return DTO properties
     */
    protected List<SourcePersistentProperty> getDtoProjectionProperties(SourcePersistentEntity entity,
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
            }).toList();
    }

    /**
     * Method result.
     *
     * @param resultType             The result type
     * @param isDto                  Is DTO
     * @param isRuntimeDtoConversion Is DTO converted at the runtime
     */
    protected record MethodResult(ClassElement resultType,
                                  boolean isDto,
                                  boolean isRuntimeDtoConversion) {
    }

}
