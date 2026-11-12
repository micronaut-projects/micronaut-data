package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class BankGraphSpec extends Specification {

    @Inject
    BankGraph bankGraph

    void "query the bank graph"() {
        when:
        // tag::find[]
        def bigSenders = bankGraph.findByTransfersAmountGreaterThan(1000) // <1>
        def connected = bankGraph.existsPathByTransfers(1L, 3L) // <2>
        // end::find[]

        then:
        bigSenders*.name == ["Bob"]
        connected
        !bankGraph.existsPathByTransfers(3L, 1L)
        bankGraph.findById(1L).get().name == "Alice"
        bankGraph.largeTransfers(100)*.source == ["Alice", "Bob"]
    }

    void "save accounts"() {
        when:
        // tag::save[]
        bankGraph.save(new Account(id: 5L, name: "Erin")) // <1>
        def erin = bankGraph.findByNameStartsWithOrderByName("Er") // <2>
        // end::save[]

        then:
        erin*.name == ["Erin"]

        when:
        bankGraph.deleteById(5L)

        then:
        !bankGraph.findById(5L).present
    }
}
