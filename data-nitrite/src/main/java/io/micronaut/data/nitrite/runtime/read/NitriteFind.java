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
package io.micronaut.data.nitrite.runtime.read;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import org.dizitart.no2.collection.FindOptions;

/**
 * A find, and what is left for the adapter to do once it returns.
 *
 * @param options the find options
 * @param sortPlan the plan to order the returned documents with, or {@code null} when the find
 *                 carries the whole sort itself
 * @since 5.2.0
 */
@Internal
public record NitriteFind(FindOptions options, @Nullable NitriteSortPlan sortPlan) {
}
