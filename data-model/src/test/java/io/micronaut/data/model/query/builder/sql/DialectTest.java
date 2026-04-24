package io.micronaut.data.model.query.builder.sql;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.DataType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static io.micronaut.data.annotation.Join.Type.ALL_TYPES;
import static io.micronaut.data.annotation.Join.Type.DEFAULT;
import static io.micronaut.data.annotation.Join.Type.FETCH;
import static io.micronaut.data.annotation.Join.Type.INNER;
import static io.micronaut.data.annotation.Join.Type.LEFT;
import static io.micronaut.data.annotation.Join.Type.LEFT_FETCH;
import static io.micronaut.data.annotation.Join.Type.RIGHT;
import static io.micronaut.data.annotation.Join.Type.RIGHT_FETCH;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialectTest {

    private static final EnumSet<Join.Type> LIMITED_JOIN_TYPES = EnumSet.of(
        DEFAULT,
        LEFT,
        LEFT_FETCH,
        RIGHT,
        RIGHT_FETCH,
        FETCH,
        INNER
    );

    @Test
    void mapsDataTypesPerDialect() {
        for (Dialect dialect : Dialect.values()) {
            DataType expectedUuidType = dialect == Dialect.MYSQL || dialect == Dialect.ORACLE ? DataType.STRING : DataType.UUID;

            assertEquals(expectedUuidType, dialect.getDataType(DataType.UUID));
            assertEquals(expectedUuidType == DataType.STRING, dialect.requiresStringUUID(DataType.UUID));
            assertEquals(dialect == Dialect.ORACLE ? DataType.DURATION : DataType.STRING, dialect.getDataType(DataType.DURATION));
            assertEquals(dialect == Dialect.ORACLE ? DataType.PERIOD : DataType.STRING, dialect.getDataType(DataType.PERIOD));
            assertEquals(DataType.BOOLEAN, dialect.getDataType(DataType.BOOLEAN));
        }
    }

    @Test
    void supportsExpectedJoinTypes() {
        assertSupportedJoinTypes(Dialect.H2, LIMITED_JOIN_TYPES);
        assertSupportedJoinTypes(Dialect.MYSQL, LIMITED_JOIN_TYPES);
        assertSupportedJoinTypes(Dialect.POSTGRES, ALL_TYPES);
        assertSupportedJoinTypes(Dialect.SQL_SERVER, ALL_TYPES);
        assertSupportedJoinTypes(Dialect.ORACLE, ALL_TYPES);
        assertSupportedJoinTypes(Dialect.SQLITE, ALL_TYPES);
        assertSupportedJoinTypes(Dialect.ANSI, ALL_TYPES);
    }

    @Test
    void allowBatchMatchesExpectedDialects() {
        assertTrue(Dialect.H2.allowBatch());
        assertTrue(Dialect.MYSQL.allowBatch());
        assertTrue(Dialect.POSTGRES.allowBatch());
        assertFalse(Dialect.SQL_SERVER.allowBatch());
        assertTrue(Dialect.ORACLE.allowBatch());
        assertFalse(Dialect.SQLITE.allowBatch());
        assertTrue(Dialect.ANSI.allowBatch());
    }

    @Test
    void supportsJsonEntityMatchesExpectedDialects() {
        assertFalse(Dialect.H2.supportsJsonEntity());
        assertFalse(Dialect.MYSQL.supportsJsonEntity());
        assertFalse(Dialect.POSTGRES.supportsJsonEntity());
        assertFalse(Dialect.SQL_SERVER.supportsJsonEntity());
        assertTrue(Dialect.ORACLE.supportsJsonEntity());
        assertFalse(Dialect.SQLITE.supportsJsonEntity());
        assertFalse(Dialect.ANSI.supportsJsonEntity());
    }

    @Test
    void supportsUpdateReturningMatchesExpectedDialects() {
        assertFalse(Dialect.H2.supportsUpdateReturning());
        assertFalse(Dialect.MYSQL.supportsUpdateReturning());
        assertTrue(Dialect.POSTGRES.supportsUpdateReturning());
        assertFalse(Dialect.SQL_SERVER.supportsUpdateReturning());
        assertTrue(Dialect.ORACLE.supportsUpdateReturning());
        assertTrue(Dialect.SQLITE.supportsUpdateReturning());
        assertFalse(Dialect.ANSI.supportsUpdateReturning());
    }

    @Test
    void supportsInsertReturningMatchesExpectedDialects() {
        assertFalse(Dialect.H2.supportsInsertReturning());
        assertFalse(Dialect.MYSQL.supportsInsertReturning());
        assertTrue(Dialect.POSTGRES.supportsInsertReturning());
        assertFalse(Dialect.SQL_SERVER.supportsInsertReturning());
        assertTrue(Dialect.ORACLE.supportsInsertReturning());
        assertTrue(Dialect.SQLITE.supportsInsertReturning());
        assertFalse(Dialect.ANSI.supportsInsertReturning());
    }

    @Test
    void supportsDeleteReturningMatchesExpectedDialects() {
        assertFalse(Dialect.H2.supportsDeleteReturning());
        assertFalse(Dialect.MYSQL.supportsDeleteReturning());
        assertTrue(Dialect.POSTGRES.supportsDeleteReturning());
        assertFalse(Dialect.SQL_SERVER.supportsDeleteReturning());
        assertTrue(Dialect.ORACLE.supportsDeleteReturning());
        assertTrue(Dialect.SQLITE.supportsDeleteReturning());
        assertFalse(Dialect.ANSI.supportsDeleteReturning());
    }

    @Test
    void supportsReadOnlyMatchesExpectedDialects() {
        assertTrue(Dialect.H2.supportsReadOnly());
        assertTrue(Dialect.MYSQL.supportsReadOnly());
        assertTrue(Dialect.POSTGRES.supportsReadOnly());
        assertTrue(Dialect.SQL_SERVER.supportsReadOnly());
        assertTrue(Dialect.ORACLE.supportsReadOnly());
        assertFalse(Dialect.SQLITE.supportsReadOnly());
        assertTrue(Dialect.ANSI.supportsReadOnly());
    }

    private static void assertSupportedJoinTypes(Dialect dialect, EnumSet<Join.Type> expectedJoinTypes) {
        for (Join.Type joinType : Join.Type.values()) {
            assertEquals(expectedJoinTypes.contains(joinType), dialect.supportsJoinType(joinType),
                () -> dialect + " join support mismatch for " + joinType);
        }
    }
}
