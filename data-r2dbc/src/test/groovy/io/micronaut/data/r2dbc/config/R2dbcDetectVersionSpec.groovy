package io.micronaut.data.r2dbc.config

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionMetadata
import io.r2dbc.spi.R2dbcNonTransientResourceException
import spock.lang.Specification

class R2dbcDetectVersionSpec extends Specification {

    void "the version is read once from the version text"() {
        given:
        def metadata = Mock(ConnectionMetadata)
        def connection = Stub(Connection) {
            getMetadata() >> metadata
        }

        when:
        String version = R2dbcSchemaGenerator.detectVersion(connection, "default")

        then:
        1 * metadata.getDatabaseVersion() >> text
        version == expected

        where:
        text                                                                                         | expected
        "Oracle AI Database 26ai Free Release 23.26.3.0.0 - Develop, Learn, and Run for Free\nVersion 23.26.3.0.0" | "23.26"
        "no version here"                                                                            | null
    }

    void "a failure to read the version falls back to no target version"() {
        given:
        def connection = Stub(Connection) {
            getMetadata() >> Stub(ConnectionMetadata) {
                getDatabaseVersion() >> { throw new R2dbcNonTransientResourceException("ORA-17008: Closed connection") }
            }
        }

        expect:
        R2dbcSchemaGenerator.detectVersion(connection, "default") == null
    }
}
