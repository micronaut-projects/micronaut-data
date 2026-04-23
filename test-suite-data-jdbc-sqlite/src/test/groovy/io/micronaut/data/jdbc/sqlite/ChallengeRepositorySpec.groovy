package io.micronaut.data.jdbc.sqlite

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest(rollback = false)
@SQLiteDBProperties
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
