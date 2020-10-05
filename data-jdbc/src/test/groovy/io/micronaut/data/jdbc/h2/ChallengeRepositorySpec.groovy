package io.micronaut.data.jdbc.h2

import io.micronaut.test.annotation.MicronautTest
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest(rollback = false)
@H2DBProperties
class ChallengeRepositorySpec extends Specification {

    @Inject
    @Shared
    ChallengeRepository repository

    @Issue("https://github.com/micronaut-projects/micronaut-data/issues/457")
    void "query with multiple joins is successful"() {
        when:
        repository.findById(1L)

        then:
        noExceptionThrown()
    }

}
