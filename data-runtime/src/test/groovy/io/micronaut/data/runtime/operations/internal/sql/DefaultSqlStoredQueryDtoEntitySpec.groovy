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
package io.micronaut.data.runtime.operations.internal.sql

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.convert.ConversionService
import io.micronaut.data.model.entities.Author
import io.micronaut.data.model.entities.Book
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersStoredQuerySpec
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import spock.lang.Specification

class DefaultSqlStoredQueryDtoEntitySpec extends Specification {

    RuntimePersistentEntity<Book> book = new RuntimePersistentEntity<>(Book)
    RuntimePersistentEntity<Author> author = new RuntimePersistentEntity<>(Author)
    AnnotationMetadata metadata = AnnotationMetadata.EMPTY_METADATA
    DefaultSqlStoredQuery<Book, Object> storedQuery = new DefaultSqlStoredQuery<>(
        new DefaultBindableParametersStoredQuerySpec.SimpleStoredQuery([]), book, new SqlQueryBuilder(), ConversionService.SHARED)

    void "nothing is cached before a DTO entity is stored"() {
        expect:
            storedQuery.getDtoPersistentEntity(metadata, book, author) == null
    }

    void "the stored DTO entity is returned for the same inputs"() {
        given:
            def dto = new RuntimePersistentEntity<>(Author)
            storedQuery.setDtoPersistentEntity(metadata, book, author, dto)
        expect:
            storedQuery.getDtoPersistentEntity(metadata, book, author).is(dto)
    }

    void "other instances of the inputs get a DTO entity built for them"() {
        given:
            storedQuery.setDtoPersistentEntity(metadata, book, author, new RuntimePersistentEntity<>(Author))
        expect:
            storedQuery.getDtoPersistentEntity(new MutableAnnotationMetadata(), book, author) == null
            storedQuery.getDtoPersistentEntity(metadata, new RuntimePersistentEntity<>(Book), author) == null
            storedQuery.getDtoPersistentEntity(metadata, book, new RuntimePersistentEntity<>(Author)) == null
    }
}
