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
package io.micronaut.data.processor.visitors

import io.micronaut.data.annotation.DataTransformer
import io.micronaut.data.annotation.sql.ColumnTransformer

class GeneratedETagUtilsSpec extends AbstractDataSpec {

    void "synthesis is idempotent when generated transformer metadata already exists"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.sql.ETaggable;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
@ETaggable
record Book(@Id Long id,
    String title,
    @GeneratedETag(function = "SYS_ROW_ETAG") String etag) {}
''')
        def properties = new ArrayList(entity.persistentProperties)
        def etag = properties.find { it.name == "etag" }

        when:
        GeneratedETagUtils.synthesizeColumnTransformer(entity, properties)

        then:
        etag != null
        def readExpression = etag.annotationMetadata.stringValue(ColumnTransformer, "read").get()
        etag.annotationMetadata.stringValue(DataTransformer, "read").get() == readExpression

        when:
        GeneratedETagUtils.synthesizeColumnTransformer(entity, properties)

        then:
        def readExpressionAfter = etag.annotationMetadata.stringValue(ColumnTransformer, "read").get()
        readExpressionAfter == readExpression
        etag.annotationMetadata.stringValue(DataTransformer, "read").get() == readExpressionAfter
    }

    void "idempotent synthesis can resolve generated ETag from version property"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.sql.ETaggable;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
@ETaggable
record Book(@Id Long id,
    String title,
    @GeneratedETag(function = "SYS_ROW_ETAG") String etag) {}
''')
        def properties = new ArrayList(entity.persistentProperties)
        GeneratedETagUtils.synthesizeColumnTransformer(entity, properties)
        def etag = properties.find { it.name == "etag" }
        def readExpression = etag.annotationMetadata.stringValue(ColumnTransformer, "read").get()

        when:
        GeneratedETagUtils.synthesizeColumnTransformer(entity, properties.findAll { it.name != "etag" })

        then:
        etag.annotationMetadata.stringValue(ColumnTransformer, "read").get() == readExpression
        etag.annotationMetadata.stringValue(DataTransformer, "read").get() == readExpression
    }

    void "synthesis includes identity property missing from supplied property list"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.sql.ETaggable;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
@ETaggable
record Book(@Id Long id,
    String title,
    @GeneratedETag(function = "SYS_ROW_ETAG") String etag) {}
''')
        def etag = entity.persistentProperties.find { it.name == "etag" }

        when:
        GeneratedETagUtils.synthesizeColumnTransformer(entity, [etag])

        then:
        etag.annotationMetadata.stringValue(ColumnTransformer, "read").get() == 'SYS_ROW_ETAG(@.id, @.title)'
        etag.annotationMetadata.stringValue(DataTransformer, "read").get() == 'SYS_ROW_ETAG(@.id, @.title)'
    }

    void "matching read transformer with custom write transformer is still a conflict"() {
        when:
        buildEntity('test.Book', '''
import io.micronaut.data.annotation.DataTransformer;
import io.micronaut.data.annotation.sql.ColumnTransformer;
import io.micronaut.data.annotation.sql.ETaggable;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
@ETaggable
record Book(@Id Long id,
    String title,
    @Version
    @GeneratedValue
    @ColumnTransformer(read = "SYS_ROW_ETAG(@.id, @.title)", write = "CUSTOM_WRITE(?)")
    @DataTransformer(read = "SYS_ROW_ETAG(@.id, @.title)")
    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag) {}
''')

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("@GeneratedETag cannot be combined with @ColumnTransformer or @DataTransformer on entity test.Book: etag")
    }

    void "entity without generated ETag is ignored"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.sql.ETaggable;

@MappedEntity
@ETaggable
record Book(@Id Long id,
    String title) {}
''')

        when:
        GeneratedETagUtils.synthesizeColumnTransformer(entity, new ArrayList(entity.persistentProperties))

        then:
        noExceptionThrown()
        !entity.hasVersion()
    }
}
