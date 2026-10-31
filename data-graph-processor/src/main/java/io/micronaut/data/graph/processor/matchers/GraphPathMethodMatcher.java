/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.graph.processor.matchers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.GraphRepository;
import io.micronaut.data.graph.annotation.PathLimit;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.graph.model.GraphPath;
import io.micronaut.data.graph.model.GraphRelationship;
import io.micronaut.data.graph.model.query.PathLimitParameter;
import io.micronaut.data.graph.model.query.PathQueryDefinition;
import io.micronaut.data.graph.model.query.builder.CypherQueryBuilder;
import io.micronaut.data.graph.model.query.builder.GraphQueryBuilder;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.model.criteria.impl.SourceParameterExpressionImpl;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.Utils;
import io.micronaut.data.processor.visitors.finders.FindersUtils;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.MethodMatcher;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implements path and relationship finders on graph repositories.
 *
 * <ul>
 *     <li>{@code findShortestPath} and {@code findPath}: one shortest path between two nodes.</li>
 *     <li>{@code findAllShortestPaths}: every shortest path between two nodes.</li>
 *     <li>{@code findAllPaths} and {@code findPaths}: every path up to a {@link PathLimit}, shortest first.</li>
 *     <li>{@code existsPath} and {@code countPaths}: whether a path exists, and how many paths there are.</li>
 *     <li>{@code findRelationship} and {@code findRelationships}: relationships between two nodes, or between any
 *     nodes of the entity when no endpoints are given.</li>
 * </ul>
 *
 * <p>A suffix {@code By<Relationship>} restricts the traversal to the relationship type of a property of the
 * entity, for example {@code findShortestPathByFriends}. Several properties are joined with {@code Or}.
 * The endpoints are the first two parameters, either entities or identifiers.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class GraphPathMethodMatcher implements MethodMatcher {

    private static final String PREFIXES = "(?:find|get|query|search|read|retrieve|list)";
    // The relationship token stops at OrderBy, which path finders do not support and report
    private static final String SUFFIX = "(?:By([A-Z]\\w*?))?(?<order>OrderBy\\w*)?$";
    private static final Pattern FIND_PATTERN = Pattern.compile("^" + PREFIXES + "(ShortestPath|AllShortestPaths|AllPaths|Paths|Path|Relationships|Relationship)" + SUFFIX);
    private static final Pattern EXISTS_PATTERN = Pattern.compile("^(?:exists|has)(Path|Relationship)" + SUFFIX);
    private static final Pattern COUNT_PATTERN = Pattern.compile("^count(Paths|Relationships)" + SUFFIX);

    @Override
    public int getOrder() {
        // Before the finder matchers, which would read the method name as a property query
        return DEFAULT_POSITION - 1500;
    }

    @Override
    public @Nullable MethodMatch match(MethodMatchContext matchContext) {
        if (!matchContext.getRepositoryClass().hasStereotype(GraphRepository.class)
            || matchContext.getMethodElement().hasAnnotation(Query.class)) {
            return null;
        }
        String name = matchContext.getMethodElement().getName();
        Matcher matcher = FIND_PATTERN.matcher(name);
        if (matcher.matches()) {
            if (isPropertyFinder(matchContext, matcher.group(1))) {
                return null;
            }
            rejectOrdering(name, matcher);
            return context -> buildMatchInfo(context, kindOf(matcher.group(1)), PathQueryDefinition.Result.ELEMENTS, matcher.group(2));
        }
        Matcher exists = EXISTS_PATTERN.matcher(name);
        if (exists.matches()) {
            rejectOrdering(name, exists);
            PathQueryDefinition.Kind kind = exists.group(1).equals("Path") ? PathQueryDefinition.Kind.SHORTEST : PathQueryDefinition.Kind.RELATIONSHIPS;
            return context -> buildMatchInfo(context, kind, PathQueryDefinition.Result.EXISTS, exists.group(2));
        }
        Matcher count = COUNT_PATTERN.matcher(name);
        if (count.matches()) {
            rejectOrdering(name, count);
            PathQueryDefinition.Kind kind = count.group(1).equals("Paths") ? PathQueryDefinition.Kind.ALL : PathQueryDefinition.Kind.RELATIONSHIPS;
            return context -> buildMatchInfo(context, kind, PathQueryDefinition.Result.COUNT, count.group(2));
        }
        return null;
    }

    /**
     * Whether the method is an ordinary finder on a property of the entity that happens to be named like a path
     * query, for example {@code findPathByName} on an entity with a {@code path} property. Such a method does not
     * return paths or relationships and is left to the finder matchers.
     *
     * @param matchContext The match context
     * @param token        The path token of the method name, for example {@code Path}
     * @return Whether the finder matchers should match the method instead
     */
    private static boolean isPropertyFinder(MethodMatchContext matchContext, String token) {
        String propertyName = Objects.requireNonNullElse(NameUtils.decapitalize(token), token);
        if (matchContext.getRootEntity().getPropertyByName(propertyName) == null) {
            return false;
        }
        ClassElement returnType = matchContext.getReturnType();
        return !refersTo(returnType, GraphPath.class) && !refersTo(returnType, GraphRelationship.class);
    }

    private static boolean refersTo(ClassElement type, Class<?> expected) {
        return type.isAssignable(expected) || type.getFirstTypeArgument().map(argument -> refersTo(argument, expected)).orElse(false);
    }

    private static void rejectOrdering(String name, Matcher matcher) {
        if (matcher.group("order") != null) {
            throw new MatchFailedException("Path and relationship finders cannot be ordered, found [" + name
                + "]: sort the result in the application, or declare the query with @Query");
        }
    }

    private static PathQueryDefinition.Kind kindOf(String token) {
        return switch (token) {
            case "ShortestPath", "Path" -> PathQueryDefinition.Kind.SHORTEST;
            case "AllShortestPaths" -> PathQueryDefinition.Kind.ALL_SHORTEST;
            case "AllPaths", "Paths" -> PathQueryDefinition.Kind.ALL;
            default -> PathQueryDefinition.Kind.RELATIONSHIPS;
        };
    }

    private MethodMatchInfo buildMatchInfo(MethodMatchContext matchContext,
                                           PathQueryDefinition.Kind kind,
                                           PathQueryDefinition.Result result,
                                           @Nullable String relationshipsToken) {
        SourcePersistentEntity entity = matchContext.getRootEntity();
        List<Association> relationships = resolveRelationships(entity, relationshipsToken);
        Direction direction = resolveDirection(relationships);

        ParameterElement[] allParameters = matchContext.getParameters();
        int maxDepth = matchContext.getMethodElement().intValue(PathLimit.class).orElse(-1);
        Endpoints parameters = endpoints(matchContext, kind, maxDepth);
        List<ParameterElement> endpoints = parameters.endpoints();
        int limitParameterIndex = parameters.limitParameterIndex();

        BindingParameter start = endpoints.isEmpty() ? null : endpointParameter(matchContext, entity, allParameters, endpoints.get(0));
        BindingParameter end = endpoints.isEmpty() ? null : endpointParameter(matchContext, entity, allParameters, endpoints.get(1));
        PathQueryDefinition definition = new PathQueryDefinition(
            entity, kind, result, relationships, direction, maxDepth, limitParameterIndex >= 0, start, end
        );
        GraphQueryBuilder queryBuilder = queryBuilder(matchContext);

        DataMethod.OperationType operationType = switch (result) {
            case COUNT -> DataMethod.OperationType.COUNT;
            case EXISTS -> DataMethod.OperationType.EXISTS;
            default -> DataMethod.OperationType.QUERY;
        };
        FindersUtils.InterceptorMatch entry = switch (result) {
            case COUNT -> FindersUtils.pickCountInterceptor(matchContext, matchContext.getReturnType());
            case EXISTS -> FindersUtils.pickExistsInterceptor(matchContext, matchContext.getReturnType());
            default -> FindersUtils.resolveInterceptorTypeByOperationType(false, false, operationType, matchContext);
        };
        ClassElement resultType = resultType(matchContext, kind, result, entry.returnType());

        MethodMatchInfo methodMatchInfo = new MethodMatchInfo(operationType, resultType, entry.interceptor());
        methodMatchInfo.queryResult(buildPathQuery(queryBuilder, definition));
        if (result == PathQueryDefinition.Result.ELEMENTS && matchContext.getReturnType().isAssignable("io.micronaut.data.model.Page")) {
            // A page of relationships also needs their count
            PathQueryDefinition countDefinition = new PathQueryDefinition(
                entity, kind, PathQueryDefinition.Result.COUNT, relationships, direction, maxDepth, limitParameterIndex >= 0, start, end
            );
            methodMatchInfo.countQueryResult(buildPathQuery(queryBuilder, countDefinition));
        }
        if (limitParameterIndex >= 0) {
            int index = limitParameterIndex;
            matchContext.getMethodElement().annotate(PathLimitParameter.class, builder -> builder.value(index));
        }
        return methodMatchInfo;
    }

    /**
     * Finds the endpoint parameters of a path or relationship finder, and its {@link PathLimit} parameter.
     *
     * @param matchContext The match context
     * @param kind         The kind of query
     * @param maxDepth     The maximum depth set on the method, or -1
     * @return The endpoints and the index of the limit parameter, or -1
     */
    private static Endpoints endpoints(MethodMatchContext matchContext, PathQueryDefinition.Kind kind, int maxDepth) {
        List<ParameterElement> allParameters = Arrays.asList(matchContext.getParameters());
        List<ParameterElement> endpoints = new ArrayList<>(2);
        int limitParameterIndex = -1;
        for (ParameterElement parameter : matchContext.getParametersNotInRole()) {
            if (!parameter.hasAnnotation(PathLimit.class)) {
                endpoints.add(parameter);
            } else if (TypeUtils.isNumber(parameter.getType())) {
                limitParameterIndex = allParameters.indexOf(parameter);
            } else {
                throw new MatchFailedException("@PathLimit parameter [" + parameter.getName() + "] must be a number");
            }
        }
        if (!endpoints.isEmpty() && endpoints.size() != 2) {
            throw new MatchFailedException("A path or relationship finder takes two endpoint parameters, the start and the end node or their identifiers, found " + endpoints.size());
        }
        if (endpoints.isEmpty() && kind != PathQueryDefinition.Kind.RELATIONSHIPS) {
            throw new MatchFailedException("A path finder needs the start and the end node as its first two parameters");
        }
        if (kind == PathQueryDefinition.Kind.ALL && maxDepth <= 0 && limitParameterIndex < 0) {
            throw new MatchFailedException("Finding all paths needs a maximum depth: add an int parameter annotated with @PathLimit, or annotate the method with @PathLimit(n)");
        }
        return new Endpoints(endpoints, limitParameterIndex);
    }

    /**
     * Checks that a finder returning paths or relationships declares them as its result type.
     *
     * @param matchContext The match context
     * @param kind         The kind of query
     * @param result       The result of the query
     * @param resultType   The result type of the interceptor, or null
     * @return The result type
     */
    private static ClassElement resultType(MethodMatchContext matchContext,
                                           PathQueryDefinition.Kind kind,
                                           PathQueryDefinition.Result result,
                                           @Nullable ClassElement resultType) {
        if (result == PathQueryDefinition.Result.ELEMENTS) {
            Class<?> expected = kind == PathQueryDefinition.Kind.RELATIONSHIPS ? GraphRelationship.class : GraphPath.class;
            if (resultType == null || !resultType.isAssignable(expected)) {
                throw new MatchFailedException("Method must return " + expected.getSimpleName() + ", or a container of " + expected.getSimpleName() + ", found: " + (resultType == null ? "void" : resultType.getName()));
            }
        }
        return resultType != null ? resultType : matchContext.getReturnType();
    }

    private static io.micronaut.data.model.query.builder.QueryResult buildPathQuery(GraphQueryBuilder queryBuilder, PathQueryDefinition definition) {
        try {
            return queryBuilder.buildPathQuery(definition);
        } catch (IllegalArgumentException e) {
            throw new MatchFailedException(String.valueOf(e.getMessage()));
        }
    }

    private static GraphQueryBuilder queryBuilder(MethodMatchContext matchContext) {
        String builderType = matchContext.getRepositoryClass()
            .stringValue(RepositoryConfiguration.class, DataMethod.META_MEMBER_QUERY_BUILDER)
            .orElse(CypherQueryBuilder.class.getName());
        QueryBuilder queryBuilder = matchContext.getQueryBuilder();
        if (queryBuilder instanceof GraphQueryBuilder graphQueryBuilder) {
            return graphQueryBuilder;
        }
        throw new MatchFailedException("Path finders need a graph query builder, the repository uses: " + builderType);
    }

    private static List<Association> resolveRelationships(SourcePersistentEntity entity, @Nullable String token) {
        if (token == null) {
            return List.of();
        }
        List<Association> associations = new ArrayList<>();
        for (String part : token.split("Or(?=[A-Z])")) {
            String propertyName = Objects.requireNonNullElse(NameUtils.decapitalize(part), part);
            PersistentProperty property = entity.getPropertyByName(propertyName);
            if (!(property instanceof Association association) || !GraphEntities.isRelationship(association)) {
                throw new MatchFailedException("Entity [" + entity.getSimpleName() + "] has no @Relationship property named [" + propertyName + "]");
            }
            associations.add(association);
        }
        return associations;
    }

    private static Direction resolveDirection(List<Association> relationships) {
        if (relationships.isEmpty()) {
            return Direction.UNDIRECTED;
        }
        Direction direction = GraphEntities.direction(relationships.get(0));
        for (Association association : relationships) {
            if (GraphEntities.direction(association) != direction) {
                return Direction.UNDIRECTED;
            }
        }
        return direction;
    }

    private static BindingParameter endpointParameter(MethodMatchContext matchContext,
                                                      SourcePersistentEntity entity,
                                                      ParameterElement[] allParameters,
                                                      ParameterElement parameter) {
        if (!entity.hasIdentity()) {
            throw new MatchFailedException("Entity [" + entity.getSimpleName() + "] needs an @Id to find paths between its nodes");
        }
        PersistentProperty identity = entity.getIdentity();
        PersistentPropertyPath idPath = PersistentPropertyPath.of(List.of(), identity);
        ClassElement type = parameter.getGenericType();
        boolean isEntity = type.getName().equals(entity.getName()) || type.isAssignable(entity.getName());
        ClassElement identityType = ((SourcePersistentProperty) identity).getType();
        if (!isEntity && !type.isAssignable(identityType) && !TypeUtils.areTypesCompatible(type, identityType)) {
            throw new MatchFailedException("Endpoint parameter [" + parameter.getName() + "] must be a " + entity.getSimpleName() + " or its identifier type " + identity.getTypeName());
        }
        SourceParameterExpressionImpl expression = new SourceParameterExpressionImpl(
            Utils.getConfiguredDataTypes(matchContext.getRepositoryClass()),
            allParameters,
            parameter,
            false,
            null
        );
        if (isEntity) {
            return bindingContext -> expression.bind(bindingContext.parameterBindingPath(idPath));
        }
        return expression;
    }

    /**
     * The endpoint parameters of a finder and the index of its limit parameter.
     *
     * @param endpoints           The endpoint parameters
     * @param limitParameterIndex The index of the limit parameter, or -1
     */
    private record Endpoints(List<ParameterElement> endpoints, int limitParameterIndex) {
    }
}
