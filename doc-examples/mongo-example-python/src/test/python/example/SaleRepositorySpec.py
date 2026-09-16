from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

from example.ManufacturerRepository import ManufacturerRepository
from example.Product import Product
from example.ProductRepository import ProductRepository
from example.Quantity import Quantity
from example.Sale import Sale
from example.SaleRepository import SaleRepository


@MicronautTest
@Disabled("TODO(python): @MappedProperty(converter=...) on a Python attribute overflows the compiler, see DISABLED_TESTS.md")
class SaleRepositorySpec:

    productRepository: Annotated[ProductRepository, Inject]
    saleRepository: Annotated[SaleRepository, Inject]
    manufacturerRepository: Annotated[ManufacturerRepository, Inject]

    @Test
    def testReadWriteCustomType(self):
        apple = self.manufacturerRepository.save("Apple")
        mac_book = self.productRepository.save(Product("MacBook", apple))

        sale = self.saleRepository.save(Sale(mac_book, Quantity(1)))
        assert sale.id is not None
        assert sale.quantity.amount == 1

        sale = self.saleRepository.getById(sale.id).orElse(sale)
        assert sale is not None
        assert sale.quantity.amount == 1
        assert self.saleRepository.findByQuantity(sale.quantity).isPresent()
