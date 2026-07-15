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
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Reservable;
import io.micronaut.data.annotation.Reserve;
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
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Matches explicit Oracle lock-free reservation delta methods.
 *
 * @author radovanradic
 * @since 5.1
 */
@Internal
public final class ReserveMethodMatcher implements MethodMatcher {

    @Override
    @Nullable
    public MethodMatch match(MethodMatchContext matchContext) {
        MethodElement method = matchContext.getMethodElement();
        if (!method.hasStereotype(Reserve.class)) {
            return null;
        }
        if (!(matchContext.getQueryBuilder() instanceof SqlQueryBuilder queryBuilder)
            || queryBuilder.getDialect() != Dialect.ORACLE
            || !queryBuilder.isDialectVersionAtLeast(SqlDialectOptions.ORACLE_23_26_1_VERSION)) {
            throw new MatchFailedException("@Reserve methods require Oracle dialect version 26 or later");
        }
        if (!TypeUtils.isValidBatchUpdateReturnType(method)) {
            throw new MatchFailedException("@Reserve methods only support void or number based return types");
        }
        if (!matchContext.hasRootEntity()) {
            matchContext.findImplicitRootEntity();
        }
        if (!matchContext.hasRootEntity()) {
            throw new MatchFailedException("Repository does not have a well-defined primary entity type");
        }
        SourcePersistentEntity entity = matchContext.getRootEntity();
        Reserve reserve = Objects.requireNonNull(method.synthesize(Reserve.class));
        SourcePersistentProperty property = entity.getPropertyByName(reserve.property());
        if (property == null || !property.hasAnnotation(Reservable.class)) {
            throw new MatchFailedException("@Reserve property [" + reserve.property() + "] must be annotated with @Reservable");
        }
        if (!property.getDataType().isNumeric()) {
            throw new MatchFailedException("@Reserve property [" + reserve.property() + "] must be numeric");
        }

        ParameterElement[] parameters = method.getParameters();
        List<ParameterElement> idParameters = Arrays.stream(parameters).filter(p -> p.hasAnnotation(Id.class)).toList();
        if (idParameters.size() != 1) {
            throw new MatchFailedException("@Reserve methods require exactly one @Id parameter");
        }
        List<ParameterElement> deltas = Arrays.stream(parameters).filter(p -> !p.hasAnnotation(Id.class)).toList();
        if (deltas.size() != 1) {
            throw new MatchFailedException("@Reserve methods require exactly one delta parameter");
        }
        ParameterElement delta = deltas.getFirst();

        return new UpdateCriteriaMethodMatch(List.of(), false) {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            protected <T> void addPropertiesToUpdate(List<ParameterElement> nonConsumedParameters,
                                                     MethodMatchContext context,
                                                     PersistentEntityRoot<T> root,
                                                     PersistentEntityCriteriaUpdate<T> query,
                                                     SourcePersistentEntityCriteriaBuilder cb) {
                Expression<? extends Number> column = (Expression) root.get(property.getName());
                Expression<? extends Number> amount = (Expression) cb.parameter(delta, new PersistentPropertyPath(property));
                Expression<? extends Number> expression = reserve.operation() == Reserve.Operation.INCREMENT
                    ? cb.sum(column, amount)
                    : cb.diff(column, amount);
                query.set(property.getName(), expression);
            }
        };
    }

    @Override
    public int getOrder() {
        return DEFAULT_POSITION - 500;
    }
}
