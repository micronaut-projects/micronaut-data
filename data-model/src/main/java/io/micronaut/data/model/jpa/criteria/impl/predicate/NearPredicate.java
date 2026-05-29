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
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor;
import jakarta.persistence.criteria.Expression;

/**
 * The near predicate implementation.
 *
 * @since 5.0
 */
@Internal
@SuppressWarnings("java:S1452")
public final class NearPredicate extends AbstractPredicate {

    private final Expression<?> value;
    private final Expression<?> geometry;
    private final Expression<? extends Number> distance;

    public NearPredicate(Expression<?> value,
                         Expression<?> geometry,
                         Expression<? extends Number> distance) {
        this.value = value;
        this.geometry = geometry;
        this.distance = distance;
    }

    public Expression<?> getValue() {
        return value;
    }

    public Expression<?> getGeometry() {
        return geometry;
    }

    public Expression<? extends Number> getDistance() {
        return distance;
    }

    @Override
    public void visitPredicate(PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }
}
