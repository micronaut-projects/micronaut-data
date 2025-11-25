package io.micronaut.data.processor.metamodel

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class JakartaDataMetamodelSpec extends AbstractTypeElementSpec {

    def "metamodel generator with comprehensive attribute types"() {
        when:
            def context = buildContext("""
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.GenerateJakartaDataMetamodel;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.util.UUID;

@Repository
interface TestEntityRepository extends GenericRepository<TestEntity, Long> {
}

enum TestEnum {
    VALUE1, VALUE2, VALUE3
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@GenerateJakartaDataMetamodel
@MappedEntity
class TestEntity {

    @Id
    Long id;

    // Primitive types
    boolean primitiveBoolean;
    byte primitiveByte;
    short primitiveShort;
    int primitiveInt;
    long primitiveLong;
    float primitiveFloat;
    double primitiveDouble;

    // Wrapper types
    Boolean wrapperBoolean;
    Byte wrapperByte;
    Short wrapperShort;
    Integer wrapperInt;
    Long wrapperLong;
    Float wrapperFloat;
    Double wrapperDouble;

    // String
    String stringField;

    // Big numbers
    BigInteger bigInteger;
    BigDecimal bigDecimal;

    // Temporal types
    LocalDate localDate;
    LocalDateTime localDateTime;
    LocalTime localTime;
    Year year;
    Instant instant;

    // Other types
    UUID uuid;
    TestEnum testEnum;
    byte[] byteArray;

    @Relation(value = Relation.Kind.EMBEDDED)
    FirstLast firstLast;
}

@Embeddable
record FirstLast(String first, String last) {
}

""")
        then:
            def metamodelClass = context.getClassLoader().loadClass("test._TestEntity")

            // Debug: print all fields in the class
            println "Fields in metamodel class: ${metamodelClass.getFields()*.name}"

            // Verify String constants exist for all fields
            metamodelClass.getField("ID").get(null) == "id"
            metamodelClass.getField("PRIMITIVEBOOLEAN").get(null) == "primitiveBoolean"
            metamodelClass.getField("PRIMITIVEBYTE").get(null) == "primitiveByte"
            metamodelClass.getField("PRIMITIVESHORT").get(null) == "primitiveShort"
            metamodelClass.getField("PRIMITIVEINT").get(null) == "primitiveInt"
            metamodelClass.getField("PRIMITIVELONG").get(null) == "primitiveLong"
            metamodelClass.getField("PRIMITIVEFLOAT").get(null) == "primitiveFloat"
            metamodelClass.getField("PRIMITIVEDOUBLE").get(null) == "primitiveDouble"
            metamodelClass.getField("WRAPPERBOOLEAN").get(null) == "wrapperBoolean"
            metamodelClass.getField("WRAPPERBYTE").get(null) == "wrapperByte"
            metamodelClass.getField("WRAPPERSHORT").get(null) == "wrapperShort"
            metamodelClass.getField("WRAPPERINT").get(null) == "wrapperInt"
            metamodelClass.getField("WRAPPERLONG").get(null) == "wrapperLong"
            metamodelClass.getField("WRAPPERFLOAT").get(null) == "wrapperFloat"
            metamodelClass.getField("WRAPPERDOUBLE").get(null) == "wrapperDouble"
            metamodelClass.getField("STRINGFIELD").get(null) == "stringField"
            metamodelClass.getField("BIGINTEGER").get(null) == "bigInteger"
            metamodelClass.getField("BIGDECIMAL").get(null) == "bigDecimal"
            metamodelClass.getField("LOCALDATE").get(null) == "localDate"
            metamodelClass.getField("LOCALDATETIME").get(null) == "localDateTime"
            metamodelClass.getField("LOCALTIME").get(null) == "localTime"
            metamodelClass.getField("YEAR").get(null) == "year"
            metamodelClass.getField("INSTANT").get(null) == "instant"
            metamodelClass.getField("UUID").get(null) == "uuid"
            metamodelClass.getField("TESTENUM").get(null) == "testEnum"
            metamodelClass.getField("BYTEARRAY").get(null) == "byteArray"
            metamodelClass.getField("FIRSTLAST").get(null) == "firstLast"

            // Verify all attribute fields exist and are initialized
            def fields = [
                'id', 'primitiveBoolean', 'primitiveByte', 'primitiveShort', 'primitiveInt', 'primitiveLong', 'primitiveFloat', 'primitiveDouble',
                'wrapperBoolean', 'wrapperByte', 'wrapperShort', 'wrapperInt', 'wrapperLong', 'wrapperFloat', 'wrapperDouble',
                'stringField', 'bigInteger', 'bigDecimal', 'localDate', 'localDateTime', 'localTime', 'year', 'instant',
                'uuid', 'testEnum', 'byteArray'
            ]

            fields.each { fieldName ->
                def field = metamodelClass.getField(fieldName)
                assert field.get(null) != null, "Field $fieldName should not be null"
            }

            // Verify specific field type patterns based on what should be generated
            // NumericAttribute for numeric types
            def numericFields = ['id', 'primitiveByte', 'primitiveShort', 'primitiveInt', 'primitiveLong', 'primitiveFloat', 'primitiveDouble',
                                'wrapperByte', 'wrapperShort', 'wrapperInt', 'wrapperLong', 'wrapperFloat', 'wrapperDouble',
                                'bigInteger', 'bigDecimal']
            numericFields.each { fieldName ->
                def field = metamodelClass.getField(fieldName)
                assert field.getType().getName().contains("NumericAttribute"), "Field $fieldName should be NumericAttribute"
            }

            // ComparableAttribute for boolean and comparable types
            def comparableFields = ['primitiveBoolean', 'wrapperBoolean', 'uuid', 'testEnum']
            comparableFields.each { fieldName ->
                def field = metamodelClass.getField(fieldName)
                assert field.getType().getName().contains("ComparableAttribute"), "Field $fieldName should be ComparableAttribute"
            }

            // TextAttribute for String
            def stringField = metamodelClass.getField("stringField")
            assert stringField.getType().getName().contains("TextAttribute"), "stringField should be TextAttribute"

            // TemporalAttribute for temporal types
            def temporalFields = ['localDate', 'localDateTime', 'localTime', 'year', 'instant']
            temporalFields.each { fieldName ->
                def field = metamodelClass.getField(fieldName)
                assert field.getType().getName().contains("TemporalAttribute"), "Field $fieldName should be TemporalAttribute"
            }

            // BasicAttribute for byte array
            def byteArrayField = metamodelClass.getField("byteArray")
            assert byteArrayField.getType().getName().contains("BasicAttribute"), "byteArray should be BasicAttribute"

            def firstLastField = metamodelClass.getField("firstLast")
            assert firstLastField.getType().getName().contains("NavigableAttribute")
    }

    def "metamodel generator package"() {
        when:
            JavaFiles javaFiles = new JavaFiles();
            javaFiles.add("package-info", """

@GenerateJakartaDataMetamodel
package test;

import io.micronaut.data.annotation.GenerateJakartaDataMetamodel;
""")
            javaFiles.add("TestEntity", """
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.GenerateJakartaDataMetamodel;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@MappedEntity
class TestEntity {

    @Id
    Long id;
    String name;
}

""")
            def context = buildContext(javaFiles)
        then:
            def metamodelClass = context.getClassLoader().loadClass("test._TestEntity")

            metamodelClass.getFields()*.name.sort() == ["ID", "NAME", "id", "name"]
    }

    def "metamodel generator with @StaticMetamodel annotation"() {
        when:
            def context = buildContext("""
package test;

import io.micronaut.data.annotation.GenerateJakartaDataMetamodel;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;

@Repository
interface SimpleRepository extends GenericRepository<SimpleEntity, Long> {
}

@GenerateJakartaDataMetamodel
@MappedEntity
class SimpleEntity {

    @Id
    Long id;
    String name;
    int value;
}

""")
        then:
            def metamodelClass = context.getClassLoader().loadClass("test._SimpleEntity")

            // Verify @StaticMetamodel annotation is present
            def staticMetamodelAnnotation = metamodelClass.getAnnotation(Class.forName("jakarta.data.metamodel.StaticMetamodel"))
            assert staticMetamodelAnnotation != null, "@StaticMetamodel annotation should be present"

            // Verify annotation value points to the entity class
            def entityClass = context.getClassLoader().loadClass("test.SimpleEntity")
            assert staticMetamodelAnnotation.value() == entityClass, "StaticMetamodel should reference the entity class"
    }

}
