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
package io.micronaut.data.model.jpa.criteria.impl.predicate;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor;
import jakarta.persistence.criteria.Expression;
import org.jspecify.annotations.Nullable;

/**
 * MongoDB geospatial predicate for {@code $nearSphere} queries.
 *
 * @since 5.0.0
 */
@Internal
public final class NearSpherePredicate extends AbstractPredicate {

    private final Expression<?> expression;
    private final Expression<?> geometry;
    @Nullable
    private final Expression<? extends Number> minDistance;
    @Nullable
    private final Expression<? extends Number> maxDistance;

    public NearSpherePredicate(Expression<?> expression,
                               Expression<?> geometry,
                               @Nullable Expression<? extends Number> minDistance,
                               @Nullable Expression<? extends Number> maxDistance) {
        this.expression = CriteriaUtils.requireProperty(expression);
        this.geometry = CriteriaUtils.requireIExpression(geometry);
        this.minDistance = minDistance;
        if (minDistance != null) {
            CriteriaUtils.requireNumericExpression(minDistance);
        }
        this.maxDistance = maxDistance;
        if (maxDistance != null) {
            CriteriaUtils.requireNumericExpression(maxDistance);
        }
    }

    public Expression<?> getExpression() {
        return expression;
    }

    public Expression<?> getGeometry() {
        return geometry;
    }

    public @Nullable Expression<? extends Number> getMinDistance() {
        return minDistance;
    }

    public @Nullable Expression<? extends Number> getMaxDistance() {
        return maxDistance;
    }

    @Override
    public void visitPredicate(PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }
}
