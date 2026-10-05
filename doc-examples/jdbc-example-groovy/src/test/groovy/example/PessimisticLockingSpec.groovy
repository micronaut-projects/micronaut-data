package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.util.concurrent.Executors
import java.util.concurrent.Future

@MicronautTest(transactional = false)
class PessimisticLockingSpec extends Specification {

    @Inject AccountBalanceRepository accountBalanceRepository
    @Inject ProductLockingRepository productLockingRepository
    @Inject ManufacturerRepository manufacturerRepository

    void cleanup() {
        accountBalanceRepository.deleteAll()
        productLockingRepository.deleteAll()
        manufacturerRepository.deleteAll()
    }

    void 'concurrent updates do not lose writes'() {
        given:
        Long id = accountBalanceRepository.save(new AccountBalance(100G)).id
        def executor = Executors.newFixedThreadPool(4)

        when:
        List<Future<?>> futures = (1..20).collect {
            executor.submit({ accountBalanceRepository.addToBalance(id, 10G) } as Runnable)
        }
        futures*.get()

        then:
        accountBalanceRepository.findById(id).get().balance == 300G

        cleanup:
        executor.shutdown()
    }

    void 'find methods for update'() {
        given:
        Manufacturer apple = manufacturerRepository.save("Apple")
        Product macBook = productLockingRepository.save(new Product("MacBook", apple))
        productLockingRepository.save(new Product("iPhone", apple))

        expect:
        productLockingRepository.findByIdForUpdate(macBook.id).get().manufacturer.name == "Apple"
        productLockingRepository.findAllOrderByNameForUpdate()*.name == ["MacBook", "iPhone"]
        productLockingRepository.findByNameForUpdate("iPhone")*.name == ["iPhone"]
    }
}
