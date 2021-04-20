package io.micronaut.data.jdbc.postgres

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class PostgresSchemaGenerationSpec extends Specification implements PostgresTestPropertyProvider {

    @Inject
    private PostgresOrganizationRepository repository

    void "test uuid generated value"() {
        expect:
        repository.count() == 0
    }
}
