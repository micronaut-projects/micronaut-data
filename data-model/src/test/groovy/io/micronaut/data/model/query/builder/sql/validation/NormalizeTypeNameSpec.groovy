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
package io.micronaut.data.model.query.builder.sql.validation

import spock.lang.Specification

class NormalizeTypeNameSpec extends Specification {

    void 'type name #typeName is normalized to #expected'() {
        expect:
        BaseSqlTableMappingValidator.normalizeTypeName(typeName) == expected

        where:
        typeName                                  | expected
        null                                      | ''
        'varchar(255)'                            | 'VARCHAR'
        'VARCHAR(255) NOT NULL'                   | 'VARCHAR'
        'numeric(19, 2)'                          | 'NUMERIC'
        'int4'                                    | 'INTEGER'
        '"public"."VARCHAR"(10)'                  | 'VARCHAR'
        '`varchar`(10)'                           | 'VARCHAR'
        '[nvarchar](max)'                         | 'NVARCHAR'
        'VECTOR(3, FLOAT32)'                      | 'VECTOR'
        'timestamp(6) with time zone'             | 'TIMESTAMPTZ'
        // Nested parentheses
        'GEOMETRY(POINT(4326))'                   | 'GEOMETRY'
        'GEOMETRY(POINT(4326)) NOT NULL'          | 'GEOMETRY'
        // Unbalanced parentheses: an unmatched closing one is dropped, an unclosed one removes the rest
        'VARCHAR(10'                              | 'VARCHAR'
        'VARCHAR)10'                              | 'VARCHAR10'
    }
}
