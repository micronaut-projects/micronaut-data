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
package io.micronaut.data.jdbc.mapper.generated;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.time.LocalDate;

/**
 * A record entity with an embedded value and an association among its components.
 *
 * @param id      The id
 * @param name    The name, required
 * @param count   A primitive component, which a NULL column cannot supply
 * @param since   The date
 * @param point   The embedded value
 * @param country The association
 */
@MappedEntity("generated_visit")
public record Visit(@Id @GeneratedValue Long id,
                    String name,
                    int count,
                    @Nullable LocalDate since,
                    @Nullable @Relation(Relation.Kind.EMBEDDED) GeoPoint point,
                    @Nullable @Relation(Relation.Kind.MANY_TO_ONE) Country country) {
}
