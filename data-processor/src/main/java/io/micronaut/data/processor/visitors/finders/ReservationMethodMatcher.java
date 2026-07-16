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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Reservable;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches explicit Oracle lock-free reservation delta methods.
 *
 * @author radovanradic
 * @since 5.1
 */
@Internal
public final class ReservationMethodMatcher implements MethodMatcher {

    private static final Pattern DELTA_PATTERN = Pattern.compile("(Increment|Decrement)([A-Z][A-Za-z0-9]*?)(?=And(?:Increment|Decrement)|$)");

    @Override
    @Nullable
    public MethodMatch match(MethodMatchContext matchContext) {
        MethodElement method = matchContext.getMethodElement();
        String methodName = method.getName();
        if (!methodName.startsWith("reserve")) {
            return null;
        }
        String deltaDefinition = methodName.substring("reserve".length());
        if (deltaDefinition.isEmpty()) {
            return null;
        }
        if (!(matchContext.getQueryBuilder() instanceof SqlQueryBuilder queryBuilder)
            || queryBuilder.getDialect() != Dialect.ORACLE
            || !queryBuilder.isDialectVersionAtLeast(SqlDialectOptions.ORACLE_23_26_1_VERSION)) {
            throw new MatchFailedException("Reservation methods require Oracle dialect version 23.26.1 or later");
        }
        if (!TypeUtils.isValidBatchUpdateReturnType(method)) {
            throw new MatchFailedException("Reservation methods only support void or number based return types");
        }
        if (!matchContext.hasRootEntity()) {
            matchContext.findImplicitRootEntity();
        }
        if (!matchContext.hasRootEntity()) {
            throw new MatchFailedException("Repository does not have a well-defined primary entity type");
        }
        SourcePersistentEntity entity = matchContext.getRootEntity();
        ParameterElement[] parameters = method.getParameters();
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

        List<Delta> deltas = parseDeltas(deltaDefinition, entity, parameters);
        if (deltas.isEmpty()) {
            return null;
        }

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
                    Expression<? extends Number> amount = (Expression) cb.parameter(delta.parameter(), delta.propertyPath());
                    Expression<? extends Number> expression = delta.increment()
                        ? cb.sum(column, amount)
                        : cb.diff(column, amount);
                    query.set(delta.propertyPath().getPath(), expression);
                }
            }
        };
    }

    private List<Delta> parseDeltas(String definition,
                                    SourcePersistentEntity entity,
                                    ParameterElement[] parameters) {
        Matcher matcher = DELTA_PATTERN.matcher(definition);
        List<Delta> deltas = new ArrayList<>();
        Set<String> targetPaths = new HashSet<>();
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                throw new MatchFailedException("Invalid reservation method name. Use reserveIncrement<Property>AndDecrement<Property>");
            }
            String propertyName = NameUtils.decapitalize(matcher.group(2));
            String propertyPath = entity.getPath(propertyName).orElse(propertyName);
            PersistentPropertyPath persistentPropertyPath = entity.getPropertyPath(propertyPath);
            if (persistentPropertyPath == null || !persistentPropertyPath.getProperty().getAnnotationMetadata().hasAnnotation(Reservable.class)) {
                throw new MatchFailedException("Reservation property [" + propertyName + "] must be annotated with @Reservable");
            }
            if (persistentPropertyPath.getProperty() instanceof Association
                || persistentPropertyPath.getAssociations().stream().anyMatch(association -> !association.isEmbedded())) {
                throw new MatchFailedException("Reservation methods cannot update relation property paths: [" + propertyName + "]");
            }
            if (!persistentPropertyPath.getProperty().getDataType().isNumeric()) {
                throw new MatchFailedException("Reservation property [" + propertyName + "] must be numeric");
            }
            if (!targetPaths.add(persistentPropertyPath.getPath())) {
                throw new MatchFailedException("Reservation property [" + propertyName + "] is declared more than once");
            }
            ParameterElement parameter = Arrays.stream(parameters)
                .filter(p -> !p.hasAnnotation(Id.class))
                .filter(p -> p.getName().equals(propertyName))
                .findFirst()
                .orElseThrow(() -> new MatchFailedException("Reservation property [" + propertyName + "] requires a matching delta parameter"));
            if (!TypeUtils.resolveDataType(parameter.getType(), java.util.Collections.emptyMap()).isNumeric()) {
                throw new MatchFailedException("Reservation delta parameter [" + propertyName + "] must be numeric");
            }
            deltas.add(new Delta(persistentPropertyPath, parameter, matcher.group(1).equals("Increment")));
            end = matcher.end();
            if (definition.startsWith("And", end)) {
                end += "And".length();
            }
        }
        if (end != definition.length()) {
            throw new MatchFailedException("Invalid reservation method name. Use reserveIncrement<Property>AndDecrement<Property>");
        }
        long deltaParameterCount = Arrays.stream(parameters).filter(p -> !p.hasAnnotation(Id.class)).count();
        if (deltaParameterCount != deltas.size()) {
            throw new MatchFailedException("Reservation methods require one delta parameter for each reservation property");
        }
        return deltas;
    }

    @Override
    public int getOrder() {
        return DEFAULT_POSITION - 500;
    }

    private record Delta(PersistentPropertyPath propertyPath, ParameterElement parameter, boolean increment) {
    }
}
