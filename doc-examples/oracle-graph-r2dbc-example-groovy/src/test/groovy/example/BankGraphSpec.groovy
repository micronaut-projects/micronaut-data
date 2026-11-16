package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class BankGraphSpec extends Specification {

    @Inject
    BankGraph bankGraph

    void cleanup() {
        bankGraph.deleteById(5L).block()
    }

    void "reactive repositories"() {
        when:
        // tag::reactive[]
        def bigSenders = bankGraph.save(new Account(id: 5L, name: "Erin")) // <1>
            .thenMany(bankGraph.findByTransfersAmountGreaterThan(1000)) // <2>
            .map { it.name }
            .collectList()
            .block() // <3>
        // end::reactive[]

        then:
        bigSenders == ["Bob"]
        bankGraph.findById(5L).block().name == "Erin"
        bankGraph.existsPathByTransfers(1L, 3L).block()
    }
}
