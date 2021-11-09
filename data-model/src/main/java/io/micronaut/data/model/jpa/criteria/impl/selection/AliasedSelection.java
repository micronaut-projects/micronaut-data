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

import java.util.List;

/**
 * The aliased selection.
 *
 * @param <T> The selection type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class AliasedSelection<T> implements ISelection<T>, SelectionVisitable {

    private final ISelection<T> selection;
    private final String alias;

    public AliasedSelection(ISelection<T> selection, String alias) {
        this.selection = selection;
        this.alias = alias;
    }

    @Override
    public void accept(SelectionVisitor selectionVisitor) {
        selectionVisitor.visit(this);
    }

    public ISelection<T> getSelection() {
        return selection;
    }

    @Override
    public Selection<T> alias(String name) {
        throw new IllegalStateException("Alias already assigned!");
    }

    @Override
    public boolean isCompoundSelection() {
        return selection.isCompoundSelection();
    }

    @Override
    public List<Selection<?>> getCompoundSelectionItems() {
        return selection.getCompoundSelectionItems();
    }

    @Override
    public Class<? extends T> getJavaType() {
        return selection.getJavaType();
    }

    @Override
    public String getAlias() {
        return alias;
    }
}
