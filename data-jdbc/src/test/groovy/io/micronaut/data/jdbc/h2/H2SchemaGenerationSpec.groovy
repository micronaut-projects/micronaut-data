package io.micronaut.data.jdbc.h2

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
@H2DBProperties
class H2SchemaGenerationSpec extends Specification {

    @Inject
    private H2OrganizationRepository repository

    void "test uuid generated value"() {
        expect:
        repository.count() == 0
    }
}
