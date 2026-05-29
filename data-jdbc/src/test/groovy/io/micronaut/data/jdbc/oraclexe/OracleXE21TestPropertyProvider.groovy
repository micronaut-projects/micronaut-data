package io.micronaut.data.jdbc.oraclexe

/**
 * Used for tests that need to test older Oracle version (21).
 */
trait OracleXE21TestPropertyProvider extends OracleTestPropertyProvider {

    @Override
    String dbType() {
        return "oracle-xe"
    }

    @Override
    Map<String, String> getProperties() {
        return super.getProperties() + [
            "test-resources.containers.oracle-xe.image-name": "gvenzl/oracle-xe:21-slim-faststart"
        ]
    }
}
