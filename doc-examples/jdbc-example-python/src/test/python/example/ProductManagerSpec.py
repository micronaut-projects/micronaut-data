from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import BeforeEach, Test

from example.ManufacturerRepository import ManufacturerRepository
from example.ProductManager import ProductManager
from example.ProductRepository import ProductRepository


@MicronautTest
class ProductManagerSpec:

    productManager: Annotated[ProductManager, Inject]
    productRepository: Annotated[ProductRepository, Inject]
    manufacturerRepository: Annotated[ManufacturerRepository, Inject]

    @BeforeEach
    def setupTest(self):
        self.productRepository.deleteAll()
        self.manufacturerRepository.deleteAll()

    @Test
    def testProductManager(self):
        apple = self.manufacturerRepository.save("Apple")
        self.productManager.save("VR", apple)
        product = self.productManager.find("VR")
        assert product.name == "VR"

    @Test
    def testProductManagerUsingRepo(self):
        intel = self.manufacturerRepository.save("Intel")
        self.productManager.save_using_repo("Processor", intel)
        product = self.productManager.find_using_repo("Processor")
        assert product.name == "Processor"

        product = self.productManager.find_using_repo("NonExistingProduct")
        assert product is None
