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
package io.micronaut.data.model.jpa.criteria;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.jpa.criteria.impl.selection.AliasedSelection;
import jakarta.persistence.criteria.Selection;

import java.util.Collections;
import java.util.List;

/**
 * The internal implementation of {@link Selection}.
 *
 * @param <T> The selection type
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface ISelection<T> extends Selection<T> {

    @Override
    @NonNull
    default Selection<T> alias(@NonNull String name) {
        return new AliasedSelection<>(this, name);
    }

    @Override
    @Nullable
    default String getAlias() {
        return null;
    }

    @Override
    default boolean isCompoundSelection() {
        return false;
    }

    @Override
    @NonNull
    default List<Selection<?>> getCompoundSelectionItems() {
        return Collections.emptyList();
    }
}
