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
package io.micronaut.data.model.jpa.criteria.impl.selection;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.ISelection;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitable;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitor;
import jakarta.persistence.criteria.Selection;

import java.util.Collections;
import java.util.List;

/**
 * The compound selection.
 *
 * @param <T> The compound selection
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class CompoundSelection<T> implements ISelection<T>, SelectionVisitable {

    private final List<Selection<?>> selections;

    public CompoundSelection(List<Selection<?>> selections) {
        this.selections = selections;
    }

    @Override
    public void accept(SelectionVisitor selectionVisitor) {
        selectionVisitor.visit(this);
    }

    @Override
    public boolean isCompoundSelection() {
        return true;
    }

    @Override
    public List<Selection<?>> getCompoundSelectionItems() {
        return Collections.unmodifiableList(selections);
    }

    @Override
    public Class<? extends T> getJavaType() {
        throw new IllegalStateException("Unknown");
    }

    @Override
    public Selection<T> alias(String name) {
        throw new IllegalStateException("Compound selection cannot have alias!");
    }
}
