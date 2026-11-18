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
package io.micronaut.data.search.elasticsearch;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

/**
 * An entity whose properties may be missing, for the Painless comparisons that the query DSL cannot express.
 *
 * @param id The id
 * @param code A code, or {@code null}
 * @param low The low reading, or {@code null}
 * @param high The high reading, or {@code null}
 */
@MappedEntity("readings")
@Serdeable
public record Reading(@Id String id,
                      @Nullable String code,
                      @Nullable Integer low,
                      @Nullable Integer high) {
}
