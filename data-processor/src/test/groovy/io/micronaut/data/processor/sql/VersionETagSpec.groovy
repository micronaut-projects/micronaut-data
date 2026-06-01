/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package io.micronaut.data.processor.sql

import io.micronaut.data.annotation.DataTransformer
import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Transient
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.annotation.Version
import io.micronaut.data.annotation.sql.ColumnTransformer
import io.micronaut.data.annotation.sql.ETaggable
import io.micronaut.data.annotation.sql.ETagValue
import io.micronaut.data.annotation.sql.GeneratedETag
import io.micronaut.data.annotation.sql.JoinColumn
import io.micronaut.data.model.DataType
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder

import java.time.Instant

class VersionETagSpec extends AbstractDataSpec {

    def builder = new RuntimeCriteriaBuilder()
    def queryBuilder = new SqlQueryBuilder()

    private static String readExpression(Class entityClass) {
        def entity = PersistentEntity.of(entityClass)
        def etag = entity.getPropertyByName("etag")
        etag.annotationMetadata.stringValue(ColumnTransformer, "read")
            .orElseGet(() -> etag.annotationMetadata.stringValue(DataTransformer, "read").orElse(""))
    }

    void "version property uses read ColumnTransformer in WHERE"() {
        when:
        // Build an UPDATE ... WHERE etag = ? query using local entity class
        def query = builder.createCriteriaUpdate(ETagBook)
        def root = query.from(ETagBook)
        def sql = query
                .set("title", builder.parameter(String))
                .where(builder.equal(root.get("etag"), builder.parameter(String)))
                .build(queryBuilder)
                .query
        def entity = PersistentEntity.of(ETagBook)
        def etag = entity.getPropertyByName("etag")

        then:
        // Expect the WHERE left-hand side to use the read transformer and alias replacement:
        // Note: UPDATE uses no table alias by default, so @. resolves to empty prefix -> properties without alias
        sql == 'UPDATE "book" SET "title"=? WHERE (SYS_ROW_ETAG(id, title) = ?)'

        etag
        etag.annotationMetadata.hasAnnotation(Version)
        etag.annotationMetadata.hasAnnotation(GeneratedValue)
        etag.annotationMetadata.stringValue(ColumnTransformer, "read").get() == 'SYS_ROW_ETAG(@.id, @.title)'
    }

    void "non ETag version property uses read ColumnTransformer in WHERE"() {
        when:
        def query = builder.createCriteriaUpdate(TransformedVersionBook)
        def root = query.from(TransformedVersionBook)
        def sql = query
            .set("title", builder.parameter(String))
            .where(builder.equal(root.get("version"), builder.parameter(String)))
            .build(queryBuilder)
            .query

        then:
        sql == 'UPDATE "transformed_version_book" SET "title"=? WHERE (VERSION_HASH(version) = ?)'
    }

    void "non ETag version property uses read ColumnTransformer in DELETE WHERE"() {
        when:
        def query = builder.createCriteriaDelete(TransformedVersionBook)
        def root = query.from(TransformedVersionBook)
        def sql = query
            .where(builder.equal(root.get("version"), builder.parameter(String)))
            .build(queryBuilder)
            .query

        then:
        sql == 'DELETE  FROM "transformed_version_book"  WHERE (VERSION_HASH(version) = ?)'
    }

    void "missing function uses Oracle dialect default"() {
        when:
        def query = builder.createCriteriaUpdate(ETagBookNoFunction)
        def root = query.from(ETagBookNoFunction)
        def sql = query
            .set("title", builder.parameter(String))
            .where(builder.equal(root.get("etag"), builder.parameter(String)))
            .build(new SqlQueryBuilder(Dialect.ORACLE))
            .query

        then:
        sql == 'UPDATE "BOOK_NO_FUNCTION" SET "TITLE"=? WHERE (SYS_ROW_ETAG(id, title) = ?)'
    }

    void "missing function fails for MySQL"() {
        when:
        def query = builder.createCriteriaUpdate(ETagBookNoFunction)
        def root = query.from(ETagBookNoFunction)
        query
            .set("title", builder.parameter(String))
            .where(builder.equal(root.get("etag"), builder.parameter(String)))
            .build(new SqlQueryBuilder(Dialect.MYSQL))

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("@GeneratedETag requires explicit 'function' for dialect MYSQL")
    }

    void "explicit function works for MySQL"() {
        when:
        def query = builder.createCriteriaUpdate(ETagBook)
        def root = query.from(ETagBook)
        def sql = query
            .set("title", builder.parameter(String))
            .where(builder.equal(root.get("etag"), builder.parameter(String)))
            .build(new SqlQueryBuilder(Dialect.MYSQL))
            .query

        then:
        sql == 'UPDATE `book` SET `title`=? WHERE (SYS_ROW_ETAG(id, title) = ?)'
    }

    void "select projection aliases generated ETag expression back to property"() {
        when:
        def sql = builder.createQuery(ETagBook).build(queryBuilder).query

        then:
        sql == 'SELECT etag_book_."id",SYS_ROW_ETAG(etag_book_.id, etag_book_.title) AS etag,etag_book_."title" FROM "book" etag_book_'
    }

    void "delete where uses generated ETag expression"() {
        when:
        def query = builder.createCriteriaDelete(ETagBook)
        def root = query.from(ETagBook)
        def sql = query
            .where(builder.equal(root.get("etag"), builder.parameter(String)))
            .build(queryBuilder)
            .query

        then:
        sql == 'DELETE  FROM "book"  WHERE (SYS_ROW_ETAG(id, title) = ?)'
    }

    void "schema insert and update do not bind generated ETag as physical column"() {
        when:
        def entity = PersistentEntity.of(ETagGeneratedIdBook)
        def createSql = new SqlQueryBuilder(Dialect.H2).buildCreateTableStatements(entity)[0]
        def insertSql = builder.createCriteriaInsert(ETagGeneratedIdBook).build(queryBuilder).query
        def updateSql = builder.createCriteriaUpdate(ETagGeneratedIdBook)
            .set("title", builder.parameter(String))
            .build(queryBuilder)
            .query

        then:
        createSql == 'CREATE TABLE `generated_id_book` (`id` BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,`title` VARCHAR(255) NOT NULL);'
        insertSql == 'INSERT INTO "generated_id_book" ("title") VALUES (?)'
        updateSql == 'UPDATE "generated_id_book" SET "title"=?'
    }

    void "test @GeneratedETag with @Version field in the entity"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    String name,
    @Version Long version,
    @GeneratedETag(function = "custom") String eTag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Entity with @Version field cannot have @GeneratedETag field")
    }

    void "test @GeneratedETag rejects multiple generated ETag properties"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    @ETagValue String name,
    @GeneratedETag(function = "custom") String eTag,
    @GeneratedETag(function = "custom") String secondETag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Only one field can be marked as @GeneratedETag")
    }

    void "test @GeneratedETag rejects multiple generated ETag properties when one is transient"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    @ETagValue String name,
    @GeneratedETag(function = "custom") String eTag,
    @Transient @GeneratedETag(function = "custom") String secondETag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Only one field can be marked as @GeneratedETag")
    }

    void "test @GeneratedETag rejects transient property"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    @ETagValue String name,
    @Transient @GeneratedETag(function = "custom") String eTag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("@GeneratedETag cannot be applied to a @Transient property: eTag")
    }

    void "test @GeneratedETag rejects ColumnTransformer on generated ETag property"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.sql.ColumnTransformer;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    @ETagValue String name,
    @ColumnTransformer(read = "CUSTOM_ETAG(@.e_tag)") @GeneratedETag(function = "custom") String eTag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("@GeneratedETag cannot be combined with @ColumnTransformer or @DataTransformer on entity test.MyEntity: eTag")
    }

    void "test @GeneratedETag rejects DataTransformer on generated ETag property"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.DataTransformer;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    @ETagValue String name,
    @DataTransformer(read = "CUSTOM_ETAG(@.e_tag)") @GeneratedETag(function = "custom") String eTag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("@GeneratedETag cannot be combined with @ColumnTransformer or @DataTransformer on entity test.MyEntity: eTag")
    }

    void "test @GeneratedETag rejects identity property"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record MyEntity(@Id @GeneratedETag(function = "custom") String id,
    @ETagValue String name) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("@GeneratedETag cannot be applied to an @Id property")
    }

    void "test @GeneratedETag without @ETagValue fields in the entity"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    String name,
    Long version,
    @GeneratedETag(function = "custom") String eTag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("@GeneratedETag requires at least one @ETagValue annotated field or @ETaggable on the entity")
    }

    void "test @GeneratedETag rejects non String property"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    @ETagValue String name,
    @GeneratedETag(function = "custom") Long eTag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("@GeneratedETag property must be a String")
    }

    void "test @ETagValue rejects transient property"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    @ETagValue String name,
    @ETagValue @Transient String ignored,
    @GeneratedETag(function = "custom") String eTag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Explicit @ETagValue cannot be applied to transient property: ignored")
    }

    void "test @ETagValue rejects generated non identity property"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    @ETagValue String name,
    @ETagValue @GeneratedValue String generatedCode,
    @GeneratedETag(function = "custom") String eTag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Explicit @ETagValue cannot be applied to ineligible property: generatedCode")
    }

    void "test @ETagValue rejects computed non identity property"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.sql.ColumnTransformer;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    @ETagValue String name,
    @ETagValue @ColumnTransformer(read = "UPPER(name)") String normalizedName,
    @GeneratedETag(function = "custom") String eTag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Explicit @ETagValue cannot be applied to ineligible property: normalizedName")
    }

    void "test @ETagValue rejects JSON property"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;
import io.micronaut.data.model.DataType;
import java.util.Map;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    @ETagValue String name,
    @ETagValue @TypeDef(type = DataType.JSON) Map<String, Object> attributes,
    @GeneratedETag(function = "custom") String eTag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Explicit @ETagValue cannot be applied to ineligible property: attributes")
    }

    void "test @ETagValue rejects array property"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    @ETagValue String name,
    @ETagValue String[] tags,
    @GeneratedETag(function = "custom") String eTag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Explicit @ETagValue cannot be applied to ineligible property: tags")
    }

    void "test @ETagValue rejects unsupported relation"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;
import java.util.List;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    @ETagValue @Relation(Relation.Kind.ONE_TO_MANY) List<Other> others,
    @GeneratedETag(function = "custom") String eTag) {}

@MappedEntity
record Other(@Id @GeneratedValue Long id) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Explicit @ETagValue on non-embedded, non-foreign-key association is not supported")
    }

    void "test @ETagValue rejects explicit many to many relation"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;
import java.util.List;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    @ETagValue @Relation(Relation.Kind.MANY_TO_MANY) List<Other> others,
    @GeneratedETag(function = "custom") String eTag) {}

@MappedEntity
record Other(@Id @GeneratedValue Long id) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Explicit @ETagValue on non-embedded, non-foreign-key association is not supported")
    }

    void "test @ETagValue rejects explicit inverse one to one relation"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    @ETagValue @Relation(value = Relation.Kind.ONE_TO_ONE, mappedBy = "owner") Other other,
    @GeneratedETag(function = "custom") String eTag) {}

@MappedEntity
record Other(@Id @GeneratedValue Long id) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Explicit @ETagValue on non-embedded, non-foreign-key association is not supported")
    }

    void "implicit with includeForeignKeys adds FK column to function args"() {
        when:
        def readExpr = readExpression(FkEntity)
        then:
        readExpr.contains("@.other_id")
    }

    void "implicit with includeForeignKeys adds owning one to one FK column"() {
        when:
        def readExpr = readExpression(OneToOneFkEntity)

        then:
        readExpr.contains("@.other_id")
    }

    void "implicit without includeForeignKeys does not traverse relation"() {
        when:
        def readExpr = readExpression(FkEntityNoForeignKeys)

        then:
        readExpr == 'SYS_ROW_ETAG(@.id, @.name)'
    }

    void "implicit with includeForeignKeys does not include inverse one to one relation"() {
        when:
        def readExpr = readExpression(InverseOneToOneFkEntity)

        then:
        readExpr == 'SYS_ROW_ETAG(@.id, @.name)'
    }

    void "includeForeignKeys honors exclude=true on association field"() {
        when:
        def readExpr = readExpression(FkEntityExcludeAssociation)

        then:
        !readExpr.contains("@.other_id")
    }

    void "explicit relation ETagValue includes owning foreign-key column"() {
        expect:
        readExpression(FkEntityExplicitAssociation) == 'SYS_ROW_ETAG(@.other_id)'
    }

    void "generated id is eligible but generated non-id property is excluded"() {
        expect:
        readExpression(ETagGeneratedIdBook) == 'SYS_ROW_ETAG(@.id, @.title)'
        readExpression(ETagGeneratedNonIdBook) == 'SYS_ROW_ETAG(@.id, @.title)'
        readExpression(ETagComputedNonIdBook) == 'SYS_ROW_ETAG(@.id, @.title)'
    }

    void "implicit ETaggable excludes JSON object and array fields"() {
        expect:
        readExpression(ETagImplicitExcludedFieldsBook) == 'SYS_ROW_ETAG(@.id, @.title)'
    }

    void "exclude true is allowed on otherwise ineligible properties"() {
        expect:
        readExpression(ETagExplicitExcludedIneligibleFieldsBook) == 'SYS_ROW_ETAG(@.id, @.title)'
    }

    void "auto populated scalar fields are eligible unless excluded"() {
        when:
        def readExpr = readExpression(ETagAutoPopulatedBook)

        then:
        readExpr.contains('@.created_at')
        readExpr.contains('@.updated_at')
        !readExpr.contains('ignored')
    }

    void "implicit and explicit ETag values are de-duplicated in stable order"() {
        expect:
        readExpression(ETagExplicitAndImplicitBook) == 'SYS_ROW_ETAG(@.id, @.title)'
    }

    void "embedded ETag annotations include and exclude scalar fields"() {
        expect:
        readExpression(EmbeddedETagValueOnProperty).contains('created_by')
        !readExpression(EmbeddedETagValueOnProperty).contains('ignored')
        readExpression(EmbeddedETagValueOnScalar).contains('created_by')
        !readExpression(EmbeddedETagValueOnScalar).contains('ignored')
    }

    void "implicit ETaggable includes embedded scalar fields and honors embedded property exclude"() {
        expect:
        readExpression(EmbeddedImplicitETaggable).contains('created_by')
        readExpression(EmbeddedImplicitETaggable).contains('ignored')
        readExpression(EmbeddedExcludedByProperty) == 'SYS_ROW_ETAG(@.id, @.title)'
    }

    void "includeForeignKeys adds all composite FK columns"() {
        when:
        def readExpr = readExpression(CompositeFkEntity)

        then:
        readExpr.contains('other_first_id')
        readExpr.contains('other_second_id')
    }

    void "includeForeignKeys honors explicit composite join columns"() {
        expect:
        readExpression(CompositeCustomJoinColumnsFkEntity) == 'SYS_ROW_ETAG(@.id, @.custom_first_id, @.custom_second_id)'
    }

    void "explicit relation ETagValue honors explicit composite join columns"() {
        expect:
        readExpression(CompositeCustomJoinColumnsExplicitFkEntity) == 'SYS_ROW_ETAG(@.custom_first_id, @.custom_second_id)'
    }
}

@MappedEntity
@ETaggable(includeForeignKeys = true)
class FkEntity {
    @Id
    @GeneratedValue
    @ETagValue
    Long id

    @Relation(Relation.Kind.MANY_TO_ONE)
    Other other

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity
@ETaggable(includeForeignKeys = true)
class OneToOneFkEntity {
    @Id
    @GeneratedValue
    Long id

    @Relation(Relation.Kind.ONE_TO_ONE)
    Other other

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity
@ETaggable(includeForeignKeys = true)
class InverseOneToOneFkEntity {
    @Id
    @GeneratedValue
    Long id

    String name

    @Relation(value = Relation.Kind.ONE_TO_ONE, mappedBy = "owner")
    Other other

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity
@ETaggable(includeForeignKeys = true)
class FkEntityExcludeAssociation {
    @Id
    @GeneratedValue
    Long id

    @ETagValue(exclude = true)
    @Relation(Relation.Kind.MANY_TO_ONE)
    Other other

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity
@ETaggable
class FkEntityNoForeignKeys {
    @Id
    @GeneratedValue
    Long id

    String name

    @Relation(Relation.Kind.MANY_TO_ONE)
    Other other

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity
class FkEntityExplicitAssociation {
    @Id
    @GeneratedValue
    Long id

    @ETagValue
    @Relation(Relation.Kind.MANY_TO_ONE)
    Other other

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity
class Other {
    @Id
    @GeneratedValue
    Long id
    String name
}

@MappedEntity("book")
class ETagBook {
    @ETagValue
    @Id
    Long id
    @ETagValue
    String title
    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity("book_no_function")
class ETagBookNoFunction {
    @ETagValue
    @Id
    Long id
    @ETagValue
    String title
    @GeneratedETag
    String etag
}

@MappedEntity("transformed_version_book")
class TransformedVersionBook {
    @Id
    Long id

    String title

    @Version
    @ColumnTransformer(read = "VERSION_HASH(@.version)")
    String version
}

@MappedEntity("generated_id_book")
@ETaggable
class ETagGeneratedIdBook {
    @Id
    @GeneratedValue
    Long id

    String title

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity("generated_non_id_book")
@ETaggable
class ETagGeneratedNonIdBook {
    @Id
    Long id

    String title

    @GeneratedValue
    String generatedCode

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity("computed_non_id_book")
@ETaggable
class ETagComputedNonIdBook {
    @Id
    Long id

    String title

    @ColumnTransformer(read = "UPPER(title)")
    String normalizedTitle

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity("implicit_excluded_fields_book")
@ETaggable
class ETagImplicitExcludedFieldsBook {
    @Id
    Long id

    String title

    @TypeDef(type = DataType.JSON)
    Map<String, Object> attributes

    @TypeDef(type = DataType.OBJECT)
    Object payload

    String[] tags

    byte[] bytes

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity("explicit_excluded_ineligible_fields_book")
@ETaggable
class ETagExplicitExcludedIneligibleFieldsBook {
    @Id
    Long id

    String title

    @ETagValue(exclude = true)
    @TypeDef(type = DataType.JSON)
    Map<String, Object> attributes

    @ETagValue(exclude = true)
    @TypeDef(type = DataType.OBJECT)
    Object payload

    @ETagValue(exclude = true)
    String[] tags

    @ETagValue(exclude = true)
    byte[] bytes

    @ETagValue(exclude = true)
    @Transient
    String ignoredTransient

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity("auto_populated_book")
@ETaggable
class ETagAutoPopulatedBook {
    @Id
    Long id

    String title

    @DateCreated
    Instant createdAt

    @DateUpdated
    Instant updatedAt

    @ETagValue(exclude = true)
    @DateUpdated
    Instant ignoredUpdatedAt

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity("explicit_and_implicit_book")
@ETaggable
class ETagExplicitAndImplicitBook {
    @ETagValue
    @Id
    Long id

    String title

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity
class EmbeddedETagValueOnProperty {
    @Id
    Long id

    @ETagValue
    @Relation(Relation.Kind.EMBEDDED)
    EmbeddedAudit audit

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity
class EmbeddedETagValueOnScalar {
    @Id
    Long id

    @Relation(Relation.Kind.EMBEDDED)
    EmbeddedAuditWithScalarAnnotation audit

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity
@ETaggable
class EmbeddedImplicitETaggable {
    @Id
    Long id

    @Relation(Relation.Kind.EMBEDDED)
    EmbeddedAuditWithoutAnnotations audit

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity
@ETaggable
class EmbeddedExcludedByProperty {
    @Id
    Long id

    String title

    @ETagValue(exclude = true)
    @Relation(Relation.Kind.EMBEDDED)
    EmbeddedAuditWithoutAnnotations audit

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@Embeddable
class EmbeddedAudit {
    String createdBy

    @ETagValue(exclude = true)
    String ignored
}

@Embeddable
class EmbeddedAuditWithScalarAnnotation {
    @ETagValue
    String createdBy

    String ignored
}

@Embeddable
class EmbeddedAuditWithoutAnnotations {
    String createdBy

    String ignored
}

@MappedEntity
@ETaggable(includeForeignKeys = true)
class CompositeFkEntity {
    @Id
    Long id

    @Relation(Relation.Kind.MANY_TO_ONE)
    CompositeFkOther other

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity
@ETaggable(includeForeignKeys = true)
class CompositeCustomJoinColumnsFkEntity {
    @Id
    Long id

    @Relation(Relation.Kind.MANY_TO_ONE)
    @JoinColumn(name = "custom_first_id", referencedColumnName = "first_id")
    @JoinColumn(name = "custom_second_id", referencedColumnName = "second_id")
    CompositeFkOther other

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity
class CompositeCustomJoinColumnsExplicitFkEntity {
    @Id
    Long id

    @ETagValue
    @Relation(Relation.Kind.MANY_TO_ONE)
    @JoinColumn(name = "custom_first_id", referencedColumnName = "first_id")
    @JoinColumn(name = "custom_second_id", referencedColumnName = "second_id")
    CompositeFkOther other

    @GeneratedETag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity
class CompositeFkOther {
    @EmbeddedId
    CompositeFkOtherId id
}

@Embeddable
class CompositeFkOtherId {
    Long firstId

    Long secondId
}
