package example

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.exceptions.UniqueConstraintException
import spock.lang.Specification

@MicronautTest(transactional = false)
class CatalogItemRepositorySpec extends Specification {

    @Inject CatalogItemRepository repository

    def cleanup() {
        repository.deleteAll()
    }

    // tag::unique-index-usage[]
    def "unique index rejects duplicate"() {
        given:
        repository.save(new CatalogItem("SKU-100", "Widget"))

        when:
        repository.save(new CatalogItem("SKU-100", "Different Widget"))

        then:
        DataAccessException e = thrown()
        e.cause instanceof UniqueConstraintException
    }
    // end::unique-index-usage[]
}
