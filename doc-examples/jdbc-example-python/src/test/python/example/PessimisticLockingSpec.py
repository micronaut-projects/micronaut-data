from typing import Annotated

from jakarta.inject import Inject
from java.util.concurrent import Executors
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, Test

from example.AccountBalance import AccountBalance
from example.AccountBalanceRepository import AccountBalanceRepository
from example.ManufacturerRepository import ManufacturerRepository
from example.Product import Product
from example.ProductLockingRepository import ProductLockingRepository


@MicronautTest(transactional=False)
class PessimisticLockingSpec:

    accountBalanceRepository: Annotated[AccountBalanceRepository, Inject]
    productLockingRepository: Annotated[ProductLockingRepository, Inject]
    manufacturerRepository: Annotated[ManufacturerRepository, Inject]

    @AfterEach
    def cleanup(self):
        self.accountBalanceRepository.deleteAll()
        self.productLockingRepository.deleteAll()
        self.manufacturerRepository.deleteAll()

    @Test
    def concurrentUpdatesDoNotLoseWrites(self):
        id = self.accountBalanceRepository.save(AccountBalance(100)).id

        executor = Executors.newFixedThreadPool(4)
        try:
            futures = [
                executor.submit(lambda: self.accountBalanceRepository.addToBalance(id, 10))
                for _ in range(20)
            ]
            for future in futures:
                future.get()
        finally:
            executor.shutdown()

        actual = self.accountBalanceRepository.findById(id).orElseThrow().balance
        assert actual == 300, f"balance was {actual}"

    @Test
    def findMethodsForUpdate(self):
        apple = self.manufacturerRepository.save("Apple")
        mac_book = self.productLockingRepository.save(Product("MacBook", apple))
        self.productLockingRepository.save(Product("iPhone", apple))

        found = self.productLockingRepository.findByIdForUpdate(mac_book.id).orElseThrow()
        assert found.manufacturer.name == "Apple"

        ordered = self.productLockingRepository.findAllOrderByNameForUpdate()
        assert [p.name for p in ordered] == ["MacBook", "iPhone"]

        by_name = self.productLockingRepository.findByNameForUpdate("iPhone")
        assert len(by_name) == 1
        assert all(p.name == "iPhone" for p in by_name)
