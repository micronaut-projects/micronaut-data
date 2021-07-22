package io.micronaut.data.jdbc.sqlserver

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
class SqlServerSchemaGenerationSpec extends Specification implements MSSQLTestPropertyProvider {

    @Inject
    private MSOrganizationRepository repository

    void "test uuid generated value"() {
        expect:
        repository.count() == 0
    }
}
