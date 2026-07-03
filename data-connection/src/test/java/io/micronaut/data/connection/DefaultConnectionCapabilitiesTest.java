package io.micronaut.data.connection;

import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultConnectionCapabilitiesTest {

    private final DefaultConnectionCapabilities capabilities = new DefaultConnectionCapabilities();

    @DisabledInNativeImage
    @ParameterizedTest
    @ValueSource(strings = {
        "MySQL",
        "MariaDB",
        "PostgreSQL",
        "H2",
        "SQLite",
        "Oracle",
        "Microsoft SQL Server"
    })
    void supportsReadOnlyForDifferentDatabaseProducts(String databaseProductName) {
        assertTrue(capabilities.supports(ConnectionCapabilities.Capability.READ_ONLY, connection(databaseProductName)));
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
