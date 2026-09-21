from typing import Annotated

from jakarta.inject import Inject
from java.util.concurrent import TimeUnit
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import BeforeEach, Test

from example.ManufacturerRepository import ManufacturerRepository
from example.Product import Product
from example.ProductManager import ProductManager
from example.ProductRepository import ProductRepository


@MicronautTest
class ProductRepositorySpec:

    productRepository: Annotated[ProductRepository, Inject]
    productManager: Annotated[ProductManager, Inject]
    manufacturerRepository: Annotated[ManufacturerRepository, Inject]

    @BeforeEach
    def setupTest(self):
        self.productRepository.deleteAll()
        self.manufacturerRepository.deleteAll()
        apple = self.manufacturerRepository.save("Apple")
        self.productRepository.saveAll([
            Product("MacBook", apple),
            Product("iPhone", apple),
        ])

    @Test
    def testJoinSpec(self):
        products = self.productRepository.list()
        assert all(p.manufacturer.name == "Apple" for p in products)

    @Test
    def testAsync(self):
        # tag::async[]
        total = (self.productRepository.findByNameContains("o")
                 .thenCompose(lambda product: self.productRepository.countByManufacturerName(product.manufacturer.name))
                 .get(1000, TimeUnit.SECONDS))

        assert total == 2
        # end::async[]

    @Test
    def testReactive(self):
        # tag::reactive[]
        total = (self.productRepository.queryByNameContains("o")
                 .flatMap(lambda product: self.productRepository.countDistinctByManufacturerName(product.manufacturer.name).toMaybe())
                 .defaultIfEmpty(0)
                 .blockingGet())

        assert total == 2
        # end::reactive[]

    @Test
    def testProgrammaticTransactions(self):
        apple = self.manufacturerRepository.save("Apple")
        watch = self.productManager.save("Watch", apple)
        assert self.productManager.find("Watch").name == watch.name

    @Test
    def testFindByNameSpecification(self):
        assert len(self.productRepository.find_by_name("macbook", True, False)) == 1
        assert len(self.productRepository.find_by_name("macbook", False, False)) == 0
