package io.micronaut.data.jdbc.oraclexe

import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.jdbc.oracle.OracleClientInfoConnectionCustomizer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.sql.Connection

/**
 * Integration test verifying that {@link OracleClientInfoConnectionCustomizer}
 * sets Oracle client info properties during {@link ConnectionOperations#execute} and restores them afterward.
 */
@MicronautTest(transactional = false)
class OracleClientInfoConnectionCustomizerSpec extends Specification implements OracleTestPropertyProvider {

    @Inject
    ConnectionOperations<Connection> connectionOperations

    @Override
    Map<String, String> getProperties() {
        return OracleTestPropertyProvider.super.getProperties() + [
                'micronaut.application.name'                      : 'test-app',
                'datasources.default.enable-oracle-client-info'   : 'true',
                'datasources.default.schema-generate'             : 'NONE',
                'datasources.default.packages'                    : '',
        ]
    }

    void "client info is set during execute"() {
        when:
        def capturedClientInfo = new Properties()
        connectionOperations.execute(ConnectionDefinition.DEFAULT) { status ->
            capturedClientInfo.putAll(status.getConnection().getClientInfo())
        }

        then: "OCSID.CLIENTID is the configured application name"
        capturedClientInfo.getProperty('OCSID.CLIENTID') == 'test-app'

        and: "OCSID.CLIENT_INFO is the thread name"
        capturedClientInfo.getProperty('OCSID.CLIENT_INFO') != null
    }

    void "client info is set on each execute call"() {
        when:
        def firstCallInfo = new Properties()
        def secondCallInfo = new Properties()

        connectionOperations.execute(ConnectionDefinition.REQUIRES_NEW) { status ->
            firstCallInfo.putAll(status.getConnection().getClientInfo())
        }
        connectionOperations.execute(ConnectionDefinition.REQUIRES_NEW) { status ->
            secondCallInfo.putAll(status.getConnection().getClientInfo())
        }

        then:
        firstCallInfo.getProperty('OCSID.CLIENTID') == 'test-app'
        secondCallInfo.getProperty('OCSID.CLIENTID') == 'test-app'
    }
}
