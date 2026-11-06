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
package io.micronaut.data.model.runtime.convert;

import io.micronaut.core.annotation.Internal;

/**
 * Marker for parameter converters that turn a full-text search string into a backend query. A stored
 * query bound through such a converter is a text search, so its score is a relevance rank and not a
 * vector distance.
 *
 * @since 5.4.0
 */
@Internal
public interface TextSearchQueryConverter extends AttributeConverter<String, String> {
}
