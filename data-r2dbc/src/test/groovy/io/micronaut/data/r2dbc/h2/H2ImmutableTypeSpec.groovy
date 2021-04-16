package io.micronaut.data.r2dbc.h2


import io.micronaut.data.r2dbc.ImmutablePet
import io.micronaut.data.r2dbc.ImmutablePetRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest(rollback = false)
class H2ImmutableTypeSpec extends Specification implements H2TestPropertyProvider {

    @Inject ImmutablePetRepository repository

    void "test insert instance with nullable read-only values"() {
        when:
        def result = repository.save(new ImmutablePet(null, null)).block()

        then:
        result.id
    }
}
