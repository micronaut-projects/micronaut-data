package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.tck.entities.BasicTypes;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@MicronautTest
@JavaSQLiteDBProperties
class SQLiteBasicTypesTest {

    @Inject
    SQLiteBasicTypesRepository repository;

    @Inject
    DataSource dataSource;

    @Test
    void testQueryThatReturnsNull() {
        assertFalse(repository.somethingThatMightSometimesReturnNull().isPresent());
    }

    @Test
    void testBasicTypeMappingForPrimitiveIntegerProperty() {
        assertMappedPropertyType("primitiveInteger", DataType.INTEGER);
    }

    @Test
    void testBasicTypeMappingForWrapperIntegerProperty() {
        assertMappedPropertyType("wrapperInteger", DataType.INTEGER);
    }

    @Test
    void testBasicTypeMappingForPrimitiveBooleanProperty() {
        assertMappedPropertyType("primitiveBoolean", DataType.BOOLEAN);
    }

    @Test
    void testBasicTypeMappingForWrapperBooleanProperty() {
        assertMappedPropertyType("wrapperBoolean", DataType.BOOLEAN);
    }

    @Test
    void testBasicTypeMappingForPrimitiveShortProperty() {
        assertMappedPropertyType("primitiveShort", DataType.SHORT);
    }

    @Test
    void testBasicTypeMappingForWrapperShortProperty() {
        assertMappedPropertyType("wrapperShort", DataType.SHORT);
    }

    @Test
    void testBasicTypeMappingForPrimitiveLongProperty() {
        assertMappedPropertyType("primitiveLong", DataType.LONG);
    }

    @Test
    void testBasicTypeMappingForWrapperLongProperty() {
        assertMappedPropertyType("wrapperLong", DataType.LONG);
    }

    @Test
    void testBasicTypeMappingForPrimitiveDoubleProperty() {
        assertMappedPropertyType("primitiveDouble", DataType.DOUBLE);
    }

    @Test
    void testBasicTypeMappingForWrapperDoubleProperty() {
        assertMappedPropertyType("wrapperDouble", DataType.DOUBLE);
    }

    @Test
    void testBasicTypeMappingForUuidProperty() {
        assertMappedPropertyType("uuid", DataType.UUID);
    }

    private void assertMappedPropertyType(String property, DataType type) {
        PersistentEntity entity = PersistentEntity.of(BasicTypes.class);
        var prop = entity.getPropertyByName(property);
        assertEquals(type, prop.getAnnotation(MappedProperty.class).enumValue("type", DataType.class).orElseThrow());
    }
}
