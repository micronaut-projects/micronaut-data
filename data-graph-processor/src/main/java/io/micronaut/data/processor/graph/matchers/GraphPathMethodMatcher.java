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
package io.micronaut.data.processor.graph.matchers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.GraphRepository;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.PathDirection;
import io.micronaut.data.graph.annotation.PathLimit;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.graph.model.GraphPath;
import io.micronaut.data.graph.model.GraphRelationship;
import io.micronaut.data.graph.model.query.PathDirectionParameter;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
 *     nodes of the entity when no endpoints are given, as {@link GraphRelationship} values or as the
 *     relationship entity of the traversed property.</li>
 * </ul>
 *
 * <p>A suffix {@code By<Relationship>} restricts the traversal to the relationship type of a property of the
 * entity, for example {@code findShortestPathByFriends}. Several properties are joined with {@code Or}.
 * The endpoints are the first two parameters, either entities or identifiers. A suffix {@code Between<Property>}
 * matches the endpoints by another property, for example {@code findShortestPathByFriendsBetweenName(String from, String to)},
 * and {@code Between<Start>And<End>} matches each endpoint by its own property.</p>
 *
 * <p>The end node is of the repository entity unless the name says {@code To<Entity>}, the end parameter is
 * another entity, the result is a {@code GraphRelationship<F, T>} of another entity, or the relationships named
 * after {@code By} all point at another entity: {@code findAllPathsToMovie}, {@code findShortestPath(Person from, Movie to)},
 * {@code findRelationshipsByRoles}.</p>
 *
 * <p>{@link PathDirection} on the method overrides the direction of the relationships, and on a parameter of type
 * {@link Direction} leaves the direction to the caller.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class GraphPathMethodMatcher implements MethodMatcher {

    private static final String PREFIXES = "(?:find|get|query|search|read|retrieve|list)";
    // The relationship token stops at OrderBy, which path finders do not support and report
    private static final String SUFFIX = "(?:To([A-Z]\\w*?))?(?:By([A-Z]\\w*?))?(?:Between([A-Z]\\w*))?(?<order>OrderBy\\w*)?$";
    private static final Pattern FIND_PATTERN = Pattern.compile("^" + PREFIXES + "(ShortestPath|AllShortestPaths|AllPaths|Paths|Path|Relationships|Relationship)" + SUFFIX);
    private static final Pattern EXISTS_PATTERN = Pattern.compile("^(?:exists|has)(Path|Relationship)" + SUFFIX);
    private static final Pattern COUNT_PATTERN = Pattern.compile("^count(Paths|Relationships)" + SUFFIX);
    private static final Pattern AND = Pattern.compile("And(?=[A-Z])");

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
            return context -> buildMatchInfo(context, kindOf(matcher.group(1)), PathQueryDefinition.Result.ELEMENTS, new Tokens(matcher));
        }
        Matcher exists = EXISTS_PATTERN.matcher(name);
        if (exists.matches()) {
            rejectOrdering(name, exists);
            PathQueryDefinition.Kind kind = exists.group(1).equals("Path") ? PathQueryDefinition.Kind.SHORTEST : PathQueryDefinition.Kind.RELATIONSHIPS;
            return context -> buildMatchInfo(context, kind, PathQueryDefinition.Result.EXISTS, new Tokens(exists));
        }
        Matcher count = COUNT_PATTERN.matcher(name);
        if (count.matches()) {
            rejectOrdering(name, count);
            PathQueryDefinition.Kind kind = count.group(1).equals("Paths") ? PathQueryDefinition.Kind.ALL : PathQueryDefinition.Kind.RELATIONSHIPS;
            return context -> buildMatchInfo(context, kind, PathQueryDefinition.Result.COUNT, new Tokens(count));
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
                                           Tokens tokens) {
        SourcePersistentEntity entity = matchContext.getRootEntity();
        ParameterElement[] allParameters = matchContext.getParameters();
        int maxDepth = matchContext.getMethodElement().intValue(PathLimit.class).orElse(-1);
        Endpoints parameters = endpoints(matchContext, kind, maxDepth);
        List<ParameterElement> endpoints = parameters.endpoints();

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
        boolean relationshipEntity = result == PathQueryDefinition.Result.ELEMENTS
            && kind == PathQueryDefinition.Kind.RELATIONSHIPS
            && !resultType.isAssignable(GraphRelationship.class);

        List<Association> relationships = relationshipEntity
            ? resolveRelationshipEntityAssociations(matchContext, entity, tokens.relationships(), resultType)
            : resolveRelationships(entity, tokens.relationships());
        SourcePersistentEntity endEntity = resolveEndEntity(matchContext, entity, tokens.end(), relationships, endpoints, resultType);
        Direction direction = matchContext.getMethodElement().enumValue(PathDirection.class, Direction.class)
            .orElseGet(() -> resolveDirection(relationships));
        EndpointProperties endpointProperties = resolveEndpointProperties(entity, endEntity, tokens.endpoints());

        BindingParameter start = endpoints.isEmpty() ? null : endpointParameter(matchContext, entity, endpointProperties.start(), allParameters, endpoints.get(0));
        BindingParameter end = endpoints.isEmpty() ? null : endpointParameter(matchContext, endEntity, endpointProperties.end(), allParameters, endpoints.get(1));
        PathQueryDefinition definition = new PathQueryDefinition(
            entity, kind, result, relationships, direction, maxDepth, parameters.limitParameterIndex() >= 0, start, end,
            endpointProperties.start(), endEntity.getName().equals(entity.getName()) ? null : endEntity, endpointProperties.end(),
            parameters.directionParameterIndex() >= 0, relationshipEntity
        );
        GraphQueryBuilder queryBuilder = queryBuilder(matchContext);

        MethodMatchInfo methodMatchInfo = new MethodMatchInfo(operationType, resultType, entry.interceptor());
        methodMatchInfo.queryResult(buildPathQuery(queryBuilder, definition));
        if (result == PathQueryDefinition.Result.ELEMENTS && matchContext.getReturnType().isAssignable("io.micronaut.data.model.Page")) {
            // A page of relationships also needs their count
            methodMatchInfo.countQueryResult(buildPathQuery(queryBuilder, definition.withResult(PathQueryDefinition.Result.COUNT)));
        }
        if (parameters.limitParameterIndex() >= 0) {
            int index = parameters.limitParameterIndex();
            matchContext.getMethodElement().annotate(PathLimitParameter.class, builder -> builder.value(index));
        }
        if (parameters.directionParameterIndex() >= 0) {
            int index = parameters.directionParameterIndex();
            // Without both endpoints an undirected relationship pattern returns each relationship twice
            boolean outgoingIfUndirected = kind == PathQueryDefinition.Kind.RELATIONSHIPS && endpoints.isEmpty();
            matchContext.getMethodElement().annotate(PathDirectionParameter.class, builder -> builder.value(index).member("outgoingIfUndirected", outgoingIfUndirected));
        }
        return methodMatchInfo;
    }

    /**
     * Finds the endpoint parameters of a path or relationship finder, its {@link PathLimit} parameter and its
     * {@link PathDirection} parameter.
     *
     * @param matchContext The match context
     * @param kind         The kind of query
     * @param maxDepth     The maximum depth set on the method, or -1
     * @return The endpoints and the indexes of the limit and direction parameters, or -1
     */
    private static Endpoints endpoints(MethodMatchContext matchContext, PathQueryDefinition.Kind kind, int maxDepth) {
        List<ParameterElement> allParameters = Arrays.asList(matchContext.getParameters());
        List<ParameterElement> endpoints = new ArrayList<>(2);
        int limitParameterIndex = -1;
        int directionParameterIndex = -1;
        for (ParameterElement parameter : matchContext.getParametersNotInRole()) {
            if (parameter.hasAnnotation(PathLimit.class)) {
                if (!TypeUtils.isNumber(parameter.getType())) {
                    throw new MatchFailedException("@PathLimit parameter [" + parameter.getName() + "] must be a number");
                }
                limitParameterIndex = allParameters.indexOf(parameter);
            } else if (parameter.hasAnnotation(PathDirection.class)) {
                if (!parameter.getType().getName().equals(Direction.class.getName())) {
                    throw new MatchFailedException("@PathDirection parameter [" + parameter.getName() + "] must be a " + Direction.class.getName());
                }
                directionParameterIndex = allParameters.indexOf(parameter);
            } else {
                endpoints.add(parameter);
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
        return new Endpoints(endpoints, limitParameterIndex, directionParameterIndex);
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
            if (kind == PathQueryDefinition.Kind.RELATIONSHIPS) {
                if (resultType == null || !(resultType.isAssignable(GraphRelationship.class) || resultType.hasStereotype(RelationshipProperties.class))) {
                    throw new MatchFailedException("Method must return GraphRelationship or a @RelationshipProperties entity, or a container of them, found: " + (resultType == null ? "void" : resultType.getName()));
                }
            } else if (resultType == null || !resultType.isAssignable(GraphPath.class)) {
                throw new MatchFailedException("Method must return GraphPath, or a container of GraphPath, found: " + (resultType == null ? "void" : resultType.getName()));
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

    /**
     * The relationships of a finder returning a relationship entity: those named after {@code By}, which must all be
     * of that entity, or every relationship property of that entity when no property is named.
     */
    private static List<Association> resolveRelationshipEntityAssociations(MethodMatchContext matchContext,
                                                                           SourcePersistentEntity entity,
                                                                           @Nullable String token,
                                                                           ClassElement resultType) {
        List<Association> associations = new ArrayList<>();
        if (token != null) {
            for (Association association : resolveRelationships(entity, token)) {
                if (!association.getAssociatedEntity().getName().equals(resultType.getName())) {
                    throw new MatchFailedException("Relationship property [" + association.getName() + "] of entity [" + entity.getSimpleName()
                        + "] is not a [" + resultType.getSimpleName() + "], return GraphRelationship instead");
                }
                associations.add(association);
            }
            return associations;
        }
        for (PersistentProperty property : entity.getPersistentProperties()) {
            if (property instanceof Association association && GraphEntities.isRelationship(association)
                && association.getAssociatedEntity().getName().equals(resultType.getName())) {
                associations.add(association);
            }
        }
        if (associations.isEmpty()) {
            throw new MatchFailedException("Entity [" + entity.getSimpleName() + "] has no @Relationship property of type [" + resultType.getSimpleName() + "]");
        }
        return associations;
    }

    /**
     * The entity of the end node: the one named by {@code To<Entity>}, the type of the end parameter, the node type of
     * a {@code GraphRelationship<F, T>} result that is not the repository entity, the common target of the traversed
     * relationships, or the repository entity.
     */
    private static SourcePersistentEntity resolveEndEntity(MethodMatchContext matchContext,
                                                           SourcePersistentEntity entity,
                                                           @Nullable String token,
                                                           List<Association> relationships,
                                                           List<ParameterElement> endpoints,
                                                           ClassElement resultType) {
        SourcePersistentEntity named = token == null ? null : findEntity(matchContext, entity, token);
        SourcePersistentEntity endEntity = named;
        if (endpoints.size() == 2 && TypeUtils.isEntity(endpoints.get(1).getGenericType())) {
            SourcePersistentEntity parameterEntity = matchContext.getEntity(endpoints.get(1).getGenericType());
            if (named != null && !named.getName().equals(parameterEntity.getName())) {
                throw new MatchFailedException("The end node is a [" + token + "] but parameter [" + endpoints.get(1).getName() + "] is a " + parameterEntity.getSimpleName());
            }
            endEntity = parameterEntity;
        }
        if (endEntity == null && resultType.isAssignable(GraphRelationship.class)) {
            // The node of GraphRelationship<F, T> that is not the repository entity, at whichever end the relationship points
            for (ClassElement nodeType : resultType.getTypeArguments().values()) {
                if (TypeUtils.isEntity(nodeType) && !nodeType.getName().equals(entity.getName())) {
                    endEntity = matchContext.getEntity(nodeType);
                    break;
                }
            }
        }
        if (endEntity == null) {
            endEntity = commonTarget(relationships);
        }
        if (endEntity == null) {
            return entity;
        }
        if (!endEntity.hasStereotype(Node.class)) {
            throw new MatchFailedException("The end node of a path or relationship finder must be a @Node entity, found: " + endEntity.getName());
        }
        return endEntity;
    }

    private static @Nullable SourcePersistentEntity commonTarget(List<Association> relationships) {
        SourcePersistentEntity target = null;
        for (Association association : relationships) {
            if (!(GraphEntities.targetNodeEntity(association) instanceof SourcePersistentEntity associated)) {
                return null;
            }
            if (target != null && !target.getName().equals(associated.getName())) {
                return null;
            }
            target = associated;
        }
        return target;
    }

    /**
     * Finds the entity with the given simple name among those the repository entity reaches through its relationships.
     */
    private static SourcePersistentEntity findEntity(MethodMatchContext matchContext, SourcePersistentEntity entity, String name) {
        Set<String> visited = new HashSet<>();
        Deque<SourcePersistentEntity> pending = new ArrayDeque<>();
        pending.add(entity);
        while (!pending.isEmpty()) {
            SourcePersistentEntity candidate = pending.poll();
            if (!visited.add(candidate.getName())) {
                continue;
            }
            if (candidate.getSimpleName().equalsIgnoreCase(name) || candidate.getPersistedName().equalsIgnoreCase(name)) {
                return candidate;
            }
            for (PersistentProperty property : candidate.getPersistentProperties()) {
                if (property instanceof Association association && association.getAssociatedEntity() instanceof SourcePersistentEntity associated) {
                    pending.add(associated);
                }
            }
        }
        SourcePersistentEntity resolved = matchContext.getEntityBySimplyNameResolver().apply(name);
        if (resolved == null) {
            throw new MatchFailedException("No entity named [" + name + "] is reachable from [" + entity.getSimpleName() + "] to end the path at");
        }
        return resolved;
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

    /**
     * The properties the endpoints are matched by: {@code Between<Property>} names one property of both entities,
     * {@code Between<Start>And<End>} one property of each.
     */
    private static EndpointProperties resolveEndpointProperties(SourcePersistentEntity entity, SourcePersistentEntity endEntity, @Nullable String token) {
        if (token == null) {
            return new EndpointProperties(null, null);
        }
        String[] parts = AND.split(token);
        if (parts.length > 2) {
            throw new MatchFailedException("Between names the property of the start node and the property of the end node, joined with And, found: " + token);
        }
        PersistentProperty start = resolveEndpointProperty(entity, parts[0]);
        PersistentProperty end = resolveEndpointProperty(endEntity, parts[parts.length - 1]);
        return new EndpointProperties(start, end);
    }

    private static PersistentProperty resolveEndpointProperty(SourcePersistentEntity entity, String token) {
        String propertyName = NameUtils.decapitalize(token);
        PersistentProperty property = entity.getPropertyByName(propertyName);
        if (property == null && entity.hasIdentity() && entity.getIdentity().getName().equals(propertyName)) {
            property = entity.getIdentity();
        }
        if (property == null || property instanceof Association) {
            throw new MatchFailedException("Entity [" + entity.getSimpleName() + "] has no property named [" + propertyName + "] to match path endpoints by");
        }
        return property;
    }

    private static BindingParameter endpointParameter(MethodMatchContext matchContext,
                                                      SourcePersistentEntity entity,
                                                      @Nullable PersistentProperty endpointProperty,
                                                      ParameterElement[] allParameters,
                                                      ParameterElement parameter) {
        if (endpointProperty == null && !entity.hasIdentity()) {
            throw new MatchFailedException("Entity [" + entity.getSimpleName() + "] needs an @Id to find paths between its nodes");
        }
        PersistentProperty identity = endpointProperty != null ? endpointProperty : entity.getIdentity();
        PersistentPropertyPath idPath = PersistentPropertyPath.of(List.of(), identity);
        ClassElement type = parameter.getGenericType();
        boolean isEntity = type.getName().equals(entity.getName()) || type.isAssignable(entity.getName());
        ClassElement identityType = ((SourcePersistentProperty) identity).getType();
        if (!isEntity && !type.isAssignable(identityType) && !TypeUtils.areTypesCompatible(type, identityType)) {
            String expected = endpointProperty != null ? "the type " + identity.getTypeName() + " of property [" + identity.getName() + "]" : "its identifier type " + identity.getTypeName();
            throw new MatchFailedException("Endpoint parameter [" + parameter.getName() + "] must be a " + entity.getSimpleName() + " or " + expected);
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
     * The tokens of a finder name.
     *
     * @param end           The entity the path ends at, after {@code To}, or null
     * @param relationships The relationship properties, after {@code By}, or null
     * @param endpoints     The endpoint properties, after {@code Between}, or null
     */
    private record Tokens(@Nullable String end, @Nullable String relationships, @Nullable String endpoints) {

        Tokens(Matcher matcher) {
            this(matcher.group(2), matcher.group(3), matcher.group(4));
        }
    }

    /**
     * The properties the endpoints are matched by, or null for the identity.
     *
     * @param start The property of the start node
     * @param end   The property of the end node
     */
    private record EndpointProperties(@Nullable PersistentProperty start, @Nullable PersistentProperty end) {
    }

    /**
     * The endpoint parameters of a finder and the indexes of its limit and direction parameters.
     *
     * @param endpoints               The endpoint parameters
     * @param limitParameterIndex     The index of the limit parameter, or -1
     * @param directionParameterIndex The index of the direction parameter, or -1
     */
    private record Endpoints(List<ParameterElement> endpoints, int limitParameterIndex, int directionParameterIndex) {
    }
}
