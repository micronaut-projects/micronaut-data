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
package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Introspected;

/**
 * The Json Duality View metadata.
 *
 * @param etag A unique identifier for a specific version of the document, as a string of hexadecimal characters.
 * @param asof The latest system change number (SCN) for the JSON document, as a JSON number.
 *             This records the last logical point in time at which the document was generated.
 */
@Introspected
public record Metadata(

    String etag,

    String asof
) {
}
