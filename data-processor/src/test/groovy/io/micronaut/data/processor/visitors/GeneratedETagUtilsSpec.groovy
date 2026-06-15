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
import io.micronaut.data.annotation.sql.GeneratedETag

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

    void "synthesis uses dialect default function when function is omitted"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record Book(@Id Long id,
    @ETagValue String title,
    @GeneratedETag String etag) {}
''')
        def etag = entity.persistentProperties.find { it.name == "etag" }

        when:
        GeneratedETagUtils.synthesizeColumnTransformer(entity, new ArrayList(entity.persistentProperties))

        then:
        etag.annotationMetadata.stringValue(ColumnTransformer, "read").get() ==
                "${GeneratedETag.DIALECT_DEFAULT_FUNCTION_MARKER}(@.title)"
    }

    void "synthesis honors explicit included and excluded scalar ETag values"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record Book(@Id Long id,
    @ETagValue String title,
    String subtitle,
    @ETagValue(exclude = true) String ignored,
    @GeneratedETag(function = "SYS_ROW_ETAG") String etag) {}
''')
        def etag = entity.persistentProperties.find { it.name == "etag" }

        when:
        GeneratedETagUtils.synthesizeColumnTransformer(entity, new ArrayList(entity.persistentProperties))

        then:
        etag.annotationMetadata.stringValue(ColumnTransformer, "read").get() == 'SYS_ROW_ETAG(@.title)'
    }

    void "implicit synthesis excludes generated computed and structured fields"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.annotation.sql.ColumnTransformer;
import io.micronaut.data.annotation.sql.ETaggable;
import io.micronaut.data.annotation.sql.GeneratedETag;
import io.micronaut.data.model.DataType;
import java.util.Map;

@MappedEntity
@ETaggable
record Book(@Id @GeneratedValue Long id,
    String title,
    @GeneratedValue String generatedCode,
    @ColumnTransformer(read = "UPPER(title)") String normalizedTitle,
    @TypeDef(type = DataType.JSON) Map<String, Object> attributes,
    @TypeDef(type = DataType.OBJECT) Object payload,
    String[] tags,
    byte[] bytes,
    @GeneratedETag(function = "SYS_ROW_ETAG") String etag) {}
''')
        def etag = entity.persistentProperties.find { it.name == "etag" }

        when:
        GeneratedETagUtils.synthesizeColumnTransformer(entity, new ArrayList(entity.persistentProperties))

        then:
        etag.annotationMetadata.stringValue(ColumnTransformer, "read").get() == 'SYS_ROW_ETAG(@.id, @.title)'
    }

    void "includeForeignKeys adds owning association columns and honors excluded association"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.ETaggable;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
@ETaggable(includeForeignKeys = true)
record Book(@Id Long id,
    String title,
    @Relation(Relation.Kind.MANY_TO_ONE) Author author,
    @ETagValue(exclude = true) @Relation(Relation.Kind.MANY_TO_ONE) Author ignoredAuthor,
    @GeneratedETag(function = "SYS_ROW_ETAG") String etag) {}

@MappedEntity
record Author(@Id Long id) {}
''')
        def etag = entity.persistentProperties.find { it.name == "etag" }

        when:
        GeneratedETagUtils.synthesizeColumnTransformer(entity, new ArrayList(entity.persistentProperties))

        then:
        etag.annotationMetadata.stringValue(ColumnTransformer, "read").get() == 'SYS_ROW_ETAG(@.id, @.title, @.author_id)'
    }

    void "explicit relation ETagValue uses join column name"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;
import io.micronaut.data.annotation.sql.JoinColumn;

@MappedEntity
record Book(@Id Long id,
    @ETagValue @Relation(Relation.Kind.MANY_TO_ONE) @JoinColumn(name = "writer_id") Author author,
    @GeneratedETag(function = "SYS_ROW_ETAG") String etag) {}

@MappedEntity
record Author(@Id Long id) {}
''')
        def etag = entity.persistentProperties.find { it.name == "etag" }

        when:
        GeneratedETagUtils.synthesizeColumnTransformer(entity, new ArrayList(entity.persistentProperties))

        then:
        etag.annotationMetadata.stringValue(ColumnTransformer, "read").get() == 'SYS_ROW_ETAG(@.writer_id)'
    }

    void "owning foreign key column helper falls back to naming strategy"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.Relation;

@MappedEntity
record Book(@Id Long id,
    @Relation(Relation.Kind.MANY_TO_ONE) Author author) {}

@MappedEntity
record Author(@Id Long id) {}
''')
        def association = entity.persistentProperties.find { it.name == "author" }
        def parts = new LinkedHashSet<String>()

        when:
        GeneratedETagUtils.addOwningForeignKeyColumns(entity, association, parts)

        then:
        GeneratedETagUtils.explicitJoinColumnNames(association).isEmpty()
        parts == ["author_id"] as Set
    }

    void "owning foreign key column helper uses explicit join column"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinColumn;

@MappedEntity
record Book(@Id Long id,
    @Relation(Relation.Kind.MANY_TO_ONE) @JoinColumn(name = "writer_id") Author author) {}

@MappedEntity
record Author(@Id Long id) {}
''')
        def association = entity.persistentProperties.find { it.name == "author" }
        def parts = new LinkedHashSet<String>()

        when:
        GeneratedETagUtils.addOwningForeignKeyColumns(entity, association, parts)

        then:
        GeneratedETagUtils.explicitJoinColumnNames(association) == ["writer_id"]
        parts == ["writer_id"] as Set
    }

    void "explicit join column helper ignores unnamed join column"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinColumn;

@MappedEntity
record Book(@Id Long id,
    @Relation(Relation.Kind.MANY_TO_ONE) @JoinColumn Author author) {}

@MappedEntity
record Author(@Id Long id) {}
''')
        def association = entity.persistentProperties.find { it.name == "author" }

        expect:
        GeneratedETagUtils.explicitJoinColumnNames(association).isEmpty()
    }

    void "owning foreign key column helper uses explicit composite join columns"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinColumn;

@MappedEntity
record Book(@Id Long id,
    @Relation(Relation.Kind.MANY_TO_ONE)
    @JoinColumn(name = "author_first_id", referencedColumnName = "first_id")
    @JoinColumn(name = "author_second_id", referencedColumnName = "second_id")
    Author author) {}

@MappedEntity
record Author(@EmbeddedId AuthorId id) {}

@Embeddable
record AuthorId(Long firstId, Long secondId) {}
''')
        def association = entity.persistentProperties.find { it.name == "author" }
        def parts = new LinkedHashSet<String>()

        when:
        GeneratedETagUtils.addOwningForeignKeyColumns(entity, association, parts)

        then:
        GeneratedETagUtils.explicitJoinColumnNames(association) == ["author_first_id", "author_second_id"]
        parts == ["author_first_id", "author_second_id"] as Set
    }

    void "explicit embedded ETagValue includes embedded scalars except excluded properties"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record Book(@Id Long id,
    @ETagValue @Relation(Relation.Kind.EMBEDDED) Audit audit,
    @GeneratedETag(function = "SYS_ROW_ETAG") String etag) {}

@Embeddable
record Audit(String createdBy,
    @ETagValue(exclude = true) String ignored) {}
''')
        def etag = entity.persistentProperties.find { it.name == "etag" }

        when:
        GeneratedETagUtils.synthesizeColumnTransformer(entity, new ArrayList(entity.persistentProperties))

        then:
        etag.annotationMetadata.stringValue(ColumnTransformer, "read").get() == 'SYS_ROW_ETAG(@.created_by)'
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

    void "generated ETag validation rejects invalid declarations"() {
        when:
        buildEntity('test.Book', source)

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains(message)

        where:
        message                                                                    | source
        "Only one field can be marked as @GeneratedETag"                           | '''
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record Book(@Id Long id,
    @ETagValue String title,
    @GeneratedETag(function = "SYS_ROW_ETAG") String etag,
    @GeneratedETag(function = "SYS_ROW_ETAG") String secondEtag) {}
'''
        "@GeneratedETag cannot be applied to an @Id property"                      | '''
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record Book(@Id @GeneratedETag(function = "SYS_ROW_ETAG") String id,
    @ETagValue String title) {}
'''
        "Entity with @Version field cannot have @GeneratedETag field"              | '''
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record Book(@Id Long id,
    @ETagValue String title,
    @Version Long version,
    @GeneratedETag(function = "SYS_ROW_ETAG") String etag) {}
'''
        "@GeneratedETag property must be a String"                                 | '''
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record Book(@Id Long id,
    @ETagValue String title,
    @GeneratedETag(function = "SYS_ROW_ETAG") Long etag) {}
'''
        "@GeneratedETag requires at least one @ETagValue annotated field or @ETaggable on the entity" | '''
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record Book(@Id Long id,
    String title,
    @GeneratedETag(function = "SYS_ROW_ETAG") String etag) {}
'''
        "Explicit @ETagValue cannot be applied to transient property: ignored"      | '''
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record Book(@Id Long id,
    @ETagValue String title,
    @ETagValue @Transient String ignored,
    @GeneratedETag(function = "SYS_ROW_ETAG") String etag) {}
'''
        "Explicit @ETagValue cannot be applied to ineligible property: tags"        | '''
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record Book(@Id Long id,
    @ETagValue String title,
    @ETagValue String[] tags,
    @GeneratedETag(function = "SYS_ROW_ETAG") String etag) {}
'''
        "Explicit @ETagValue on non-embedded, non-foreign-key association is not supported" | '''
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;
import java.util.List;

@MappedEntity
record Book(@Id Long id,
    @ETagValue @Relation(Relation.Kind.ONE_TO_MANY) List<Review> reviews,
    @GeneratedETag(function = "SYS_ROW_ETAG") String etag) {}

@MappedEntity
record Review(@Id Long id) {}
'''
    }
}
