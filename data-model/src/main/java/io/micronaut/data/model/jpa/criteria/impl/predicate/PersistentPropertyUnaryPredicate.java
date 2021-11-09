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
package io.micronaut.data.model.jpa.criteria.impl.predicate;

import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor;
import jakarta.persistence.criteria.Predicate;

/**
 * The property unary operation predicate implementation.
 *
 * @param <T> The property type
 * @author Denis Stepanov
 * @since 3.2
 */
public final class PersistentPropertyUnaryPredicate<T> extends AbstractPersistentPropertyPredicate<T> {

    private final PredicateUnaryOp op;

    public PersistentPropertyUnaryPredicate(PersistentPropertyPath<T> persistentPropertyPath, PredicateUnaryOp op) {
        super(persistentPropertyPath);
        this.op = op;
    }

    public PredicateUnaryOp getOp() {
        return op;
    }

    @Override
    public Predicate not() {
        PredicateUnaryOp negatedOp = op.negate();
        if (negatedOp != null) {
            return new PersistentPropertyUnaryPredicate<>(getPropertyPath(), negatedOp);
        }
        return super.not();
    }

    @Override
    public void accept(PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "PersistentPropertyUnaryPredicate{" +
                "persistentPropertyPath=" + persistentPropertyPath +
                ", op=" + op +
                '}';
    }
}
