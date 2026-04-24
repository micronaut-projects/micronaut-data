package example;

import io.micronaut.data.connection.ConnectionCapabilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteConnectionCapabilitiesTest {

    private final ConnectionCapabilities capabilities = new SqliteConnectionCapabilities();

    @DisabledInNativeImage
    @ParameterizedTest
    @CsvSource({
        "MySQL, true",
        "MariaDB, true",
        "PostgreSQL, true",
        "H2, true",
        "SQLite, false",
        "Oracle, true",
        "'Microsoft SQL Server', true"
    })
    void supportsReadOnlyForDifferentDatabaseProducts(String databaseProductName, boolean expected) {
        assertEquals(expected, capabilities.supports(ConnectionCapabilities.Capability.READ_ONLY, connection(databaseProductName)));
    }

    @DisabledInNativeImage
    @ParameterizedTest
    @CsvSource({
        "MySQL, true",
        "MariaDB, true",
        "PostgreSQL, true",
        "H2, true",
        "SQLite, false",
        "Oracle, true",
        "'Microsoft SQL Server', false"
    })
    void supportsBatchInsertForDifferentDatabaseProducts(String databaseProductName, boolean expected) {
        assertEquals(expected, capabilities.supports(ConnectionCapabilities.Capability.BATCH_INSERT, connection(databaseProductName)));
    }

    @DisabledInNativeImage
    @Test
    void supportsBatchInsertWhenDatabaseMetadataCannotBeRead()  {
        assertTrue(capabilities.supports(ConnectionCapabilities.Capability.BATCH_INSERT, connectionThrowingMetadataException()));
    }

    private Connection connection(String databaseProductName) {
        DatabaseMetaData metaData = (DatabaseMetaData) Proxy.newProxyInstance(
            DatabaseMetaData.class.getClassLoader(),
            new Class<?>[]{DatabaseMetaData.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getDatabaseProductName" -> databaseProductName;
                case "unwrap" -> null;
                case "isWrapperFor" -> false;
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        return connection(metaData);
    }

    private Connection connectionThrowingMetadataException() {
        DatabaseMetaData metaData = (DatabaseMetaData) Proxy.newProxyInstance(
            DatabaseMetaData.class.getClassLoader(),
            new Class<?>[]{DatabaseMetaData.class},
            (proxy, method, args) -> {
                if ("getDatabaseProductName".equals(method.getName())) {
                    throw new SQLException("metadata unavailable");
                }
                if ("unwrap".equals(method.getName())) {
                    return null;
                }
                if ("isWrapperFor".equals(method.getName())) {
                    return false;
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );
        return connection(metaData);
    }

    private Connection connection(DatabaseMetaData metaData) {
        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getMetaData" -> metaData;
                case "unwrap" -> null;
                case "isWrapperFor" -> false;
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }
}
