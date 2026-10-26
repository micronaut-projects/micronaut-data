/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.azure

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.document.model.query.builder.CosmosSqlQueryBuilder
import spock.lang.Specification

class CosmosLimitSpec extends Specification {

    void "test runtime pagination keeps the Cosmos OFFSET LIMIT syntax"() {
        given:
        def builder = new CosmosSqlQueryBuilder(AnnotationMetadata.EMPTY_METADATA)

        expect: "the overload that SQL prepared queries call with the method metadata"
        builder.buildLimitAndOffset(3, 0, AnnotationMetadata.EMPTY_METADATA) == " OFFSET 0 LIMIT 3 "
        builder.buildLimitAndOffset(3, 6, AnnotationMetadata.EMPTY_METADATA) == " OFFSET 6 LIMIT 3 "
        builder.buildLimitAndOffset(3, 6) == " OFFSET 6 LIMIT 3 "
    }
}
