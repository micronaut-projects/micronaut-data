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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Reservable;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.ReservationDelta;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.criteria.UpdateCriteriaMethodMatch;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches explicit Oracle lock-free reservation delta methods.
 *
 * @author radovanradic
 * @since 5.2
 */
@Internal
public final class ReservationMethodMatcher implements MethodMatcher {

    private static final Pattern DELTA_PATTERN = Pattern.compile("(Increment|Decrement)([A-Z][A-Za-z0-9]*?)(?=And(?:Increment|Decrement)|$)");

    @Override
    @Nullable
    public MethodMatch match(MethodMatchContext matchContext) {
        MethodElement method = matchContext.getMethodElement();
        String deltaDefinition = reservationDefinition(method);
        if (deltaDefinition == null) {
            return null;
        }
        validateOracleDialect(matchContext);
        validateReturnType(method);
        SourcePersistentEntity entity = resolveRootEntity(matchContext);
        ParameterElement[] parameters = method.getParameters();
        validateIdParameter(entity, parameters);
        List<Delta> deltas = parseDeltas(deltaDefinition, entity, parameters);
        return deltas.isEmpty() ? null : reservationUpdateMatch(deltas);
    }

    @Nullable
    private static String reservationDefinition(MethodElement method) {
        String methodName = method.getName();
        if (!methodName.startsWith("reserve")) {
            return null;
        }
        String definition = methodName.substring("reserve".length());
        return definition.isEmpty() ? null : definition;
    }

    private static void validateOracleDialect(MethodMatchContext matchContext) {
        if (!(matchContext.getQueryBuilder() instanceof SqlQueryBuilder queryBuilder)
            || queryBuilder.getDialect() != Dialect.ORACLE) {
            throw new MatchFailedException("Reservation methods require the Oracle dialect");
        }
    }

    private static void validateReturnType(MethodElement method) {
        if (!TypeUtils.isValidBatchUpdateReturnType(method)) {
            throw new MatchFailedException("Reservation methods only support void or number based return types");
        }
    }

    private static SourcePersistentEntity resolveRootEntity(MethodMatchContext matchContext) {
        if (!matchContext.hasRootEntity()) {
            matchContext.findImplicitRootEntity();
        }
        if (!matchContext.hasRootEntity()) {
            throw new MatchFailedException("Repository does not have a well-defined primary entity type");
        }
        return matchContext.getRootEntity();
    }

    private static void validateIdParameter(SourcePersistentEntity entity, ParameterElement[] parameters) {
        List<ParameterElement> idParameters = Arrays.stream(parameters).filter(p -> p.hasAnnotation(Id.class)).toList();
        if (idParameters.size() != 1) {
            throw new MatchFailedException("Reservation methods require exactly one @Id parameter");
        }
        ParameterElement idParameter = idParameters.getFirst();
        if (!entity.hasIdentity()) {
            throw new MatchFailedException("Reservation methods require an entity identity or embedded ID");
        }
        SourcePersistentProperty identity = entity.getIdentity();
        String idType = TypeUtils.getTypeName(identity.getType());
        String idParameterType = TypeUtils.getTypeName(idParameter.getType());
        if (!idType.equals(idParameterType)) {
            throw new MatchFailedException("ID type of method [" + idParameterType + "] does not match ID type of entity: " + idType);
        }
    }

    private static MethodMatch reservationUpdateMatch(List<Delta> deltas) {
        return new UpdateCriteriaMethodMatch(List.of(), false) {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            protected <T> void addPropertiesToUpdate(List<ParameterElement> nonConsumedParameters,
                                                     MethodMatchContext context,
                                                     PersistentEntityRoot<T> root,
                                                     PersistentEntityCriteriaUpdate<T> query,
                                                     SourcePersistentEntityCriteriaBuilder cb) {
                for (Delta delta : deltas) {
                    Path<?> path = root;
                    for (String segment : delta.propertyPath().getPath().split("\\.")) {
                        path = path.get(segment);
                    }
                    Expression<? extends Number> column = (Expression) path;
                    Expression<? extends Number> amount = cb.parameter(delta.parameter(), delta.propertyPath());
                    Expression<? extends Number> expression = delta.increment()
                        ? cb.sum(column, amount)
                        : cb.diff(column, amount);
                    query.set(delta.propertyPath().getPath(), new ReservationDelta(expression));
                }
            }

            @Override
            protected boolean shouldAddAutomaticUpdateAssignments() {
                return false;
            }
        };
    }

    private List<Delta> parseDeltas(String definition,
                                    SourcePersistentEntity entity,
                                    ParameterElement[] parameters) {
        Matcher matcher = DELTA_PATTERN.matcher(definition);
        List<DeltaOperation> operations = new ArrayList<>();
        Set<String> targetPaths = new HashSet<>();
        int end = 0;
        while (matcher.find()) {
            validateMatchPosition(matcher, end);
            operations.add(parseOperation(matcher, entity, parameters, targetPaths));
            end = nextMatchStart(definition, matcher.end());
        }
        validateDefinitionEnd(definition, end);
        validateDeltaParameterCount(parameters, operations);
        List<ParameterElement> assignment = resolveDeltaParameters(operations);
        List<Delta> deltas = new ArrayList<>(operations.size());
        for (int i = 0; i < operations.size(); i++) {
            DeltaOperation operation = operations.get(i);
            ParameterElement parameter = assignment.get(i);
            if (!TypeUtils.resolveDataType(parameter.getType(), Collections.emptyMap()).isNumeric()) {
                throw new MatchFailedException("Reservation delta parameter [" + operation.propertyName() + "] must be numeric");
            }
            deltas.add(new Delta(operation.propertyPath(), parameter, operation.increment()));
        }
        return deltas;
    }

    private static void validateMatchPosition(Matcher matcher, int end) {
        if (matcher.start() != end) {
            throw new MatchFailedException("Invalid reservation method name. Use reserveIncrement<Property>AndDecrement<Property>");
        }
    }

    private static DeltaOperation parseOperation(Matcher matcher,
                                                 SourcePersistentEntity entity,
                                                 ParameterElement[] parameters,
                                                 Set<String> targetPaths) {
        String propertyName = Objects.requireNonNull(NameUtils.decapitalize(matcher.group(2)), "Reservation property name must not be null");
        PersistentPropertyPath propertyPath = resolveReservationProperty(entity, propertyName);
        validateUniqueTarget(propertyName, propertyPath, targetPaths);
        String operation = matcher.group(1);
        List<ParameterElement> candidates = findDeltaParameterCandidates(parameters, propertyName, operation);
        return new DeltaOperation(propertyName, propertyPath, operation.equals("Increment"), candidates);
    }

    private static PersistentPropertyPath resolveReservationProperty(SourcePersistentEntity entity, String propertyName) {
        String propertyPath = entity.getPath(propertyName).orElse(propertyName);
        PersistentPropertyPath persistentPropertyPath = entity.getPropertyPath(propertyPath);
        if (persistentPropertyPath == null) {
            throw new MatchFailedException("Reservation property [" + propertyName + "] does not exist");
        }
        validateReservationProperty(propertyName, persistentPropertyPath);
        return persistentPropertyPath;
    }

    private static void validateReservationProperty(String propertyName, PersistentPropertyPath propertyPath) {
        if (!propertyPath.getProperty().getAnnotationMetadata().hasAnnotation(Reservable.class)) {
            throw new MatchFailedException("Reservation property [" + propertyName + "] must be annotated with @Reservable");
        }
        if (propertyPath.getProperty() instanceof Association
            || propertyPath.getAssociations().stream().anyMatch(association -> !association.isEmbedded())) {
            throw new MatchFailedException("Reservation methods cannot update relation property paths: [" + propertyName + "]");
        }
        if (!propertyPath.getProperty().getDataType().isNumeric()) {
            throw new MatchFailedException("Reservation property [" + propertyName + "] must be numeric");
        }
    }

    private static void validateUniqueTarget(String propertyName,
                                             PersistentPropertyPath propertyPath,
                                             Set<String> targetPaths) {
        if (!targetPaths.add(propertyPath.getPath())) {
            throw new MatchFailedException("Reservation property [" + propertyName + "] is declared more than once");
        }
    }

    /**
     * Finds the parameters that can be the delta of a reservation operation. A parameter matches when its name,
     * or its {@link Parameter} value, is the property name or the property name followed by the operation,
     * for example {@code balance} or {@code balanceIncrement} for {@code IncrementBalance}.
     */
    private static List<ParameterElement> findDeltaParameterCandidates(ParameterElement[] parameters,
                                                                       String propertyName,
                                                                       String operation) {
        String operationName = propertyName + operation;
        List<ParameterElement> candidates = Arrays.stream(parameters)
            .filter(p -> !p.hasAnnotation(Id.class))
            .filter(p -> {
                String name = p.stringValue(Parameter.class).orElse(p.getName());
                return name.equals(propertyName) || name.equals(operationName);
            })
            .toList();
        if (candidates.isEmpty()) {
            throw new MatchFailedException("Reservation property [" + propertyName + "] requires a matching delta parameter named ["
                + propertyName + "] or [" + operationName + "], or annotated with @Parameter(\"" + propertyName + "\") or @Parameter(\""
                + operationName + "\")");
        }
        return candidates;
    }

    /**
     * Assigns a distinct delta parameter to every operation. The assignment is resolved for all operations
     * together, so it does not depend on the order of the operations in the method name, and it must be unique.
     */
    private static List<ParameterElement> resolveDeltaParameters(List<DeltaOperation> operations) {
        List<List<ParameterElement>> assignments = new ArrayList<>();
        collectAssignments(operations, 0, new ArrayList<>(), Collections.newSetFromMap(new IdentityHashMap<>()), assignments);
        if (assignments.isEmpty()) {
            throw new MatchFailedException("Reservation delta parameters cannot be matched to distinct parameters for properties "
                + propertyNames(operations) + ". Use @Parameter to name each delta parameter after its property");
        }
        if (assignments.size() > 1) {
            throw new MatchFailedException("Reservation delta parameters are ambiguous for properties "
                + propertyNames(operations) + ". Use @Parameter to name each delta parameter after its property");
        }
        return assignments.getFirst();
    }

    private static void collectAssignments(List<DeltaOperation> operations,
                                           int index,
                                           List<ParameterElement> current,
                                           Set<ParameterElement> used,
                                           List<List<ParameterElement>> assignments) {
        if (assignments.size() > 1) {
            return;
        }
        if (index == operations.size()) {
            assignments.add(List.copyOf(current));
            return;
        }
        for (ParameterElement candidate : operations.get(index).candidates()) {
            if (used.add(candidate)) {
                current.add(candidate);
                collectAssignments(operations, index + 1, current, used, assignments);
                current.removeLast();
                used.remove(candidate);
            }
        }
    }

    private static List<String> propertyNames(List<DeltaOperation> operations) {
        return operations.stream().map(DeltaOperation::propertyName).toList();
    }

    private static int nextMatchStart(String definition, int end) {
        return definition.startsWith("And", end) ? end + "And".length() : end;
    }

    private static void validateDefinitionEnd(String definition, int end) {
        if (end != definition.length()) {
            throw new MatchFailedException("Invalid reservation method name. Use reserveIncrement<Property>AndDecrement<Property>");
        }
    }

    private static void validateDeltaParameterCount(ParameterElement[] parameters, List<DeltaOperation> operations) {
        long deltaParameterCount = Arrays.stream(parameters).filter(p -> !p.hasAnnotation(Id.class)).count();
        if (deltaParameterCount != operations.size()) {
            throw new MatchFailedException("Reservation methods require one delta parameter for each reservation property");
        }
    }

    @Override
    public int getOrder() {
        return DEFAULT_POSITION - 500;
    }

    private record Delta(PersistentPropertyPath propertyPath, ParameterElement parameter, boolean increment) {
    }

    private record DeltaOperation(String propertyName,
                                  PersistentPropertyPath propertyPath,
                                  boolean increment,
                                  List<ParameterElement> candidates) {
    }
}
