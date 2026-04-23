package io.micronaut.data.jdbc.sqlite

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
@SQLiteDBProperties
class SQLiteSchemaGenerationSpec extends Specification {

    @Inject
    private SQLiteOrganizationRepository repository

    void "test uuid generated value"() {
        expect:
        repository.count() == 0
    }
}
