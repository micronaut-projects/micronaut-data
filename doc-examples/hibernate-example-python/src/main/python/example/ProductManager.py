from jakarta.inject import Singleton
from jakarta.persistence import EntityManager
from micronaut.transaction import TransactionOperations
from org.hibernate import Session

from example.Manufacturer import Manufacturer
from example.Product import Product


@Singleton
class ProductManager:

    def __init__(self, entity_manager: EntityManager, transaction_manager: TransactionOperations[Session]):  # <1>
        self.entity_manager = entity_manager
        self.transaction_manager = transaction_manager

    def save(self, name: str, manufacturer: Manufacturer) -> Product:
        def persist(status):  # <2>
            product = Product(name, manufacturer)
            self.entity_manager.persist(product)
            return product
        return self.transaction_manager.executeWrite(persist)

    def find(self, name: str) -> Product:
        return self.transaction_manager.executeRead(  # <3>
            lambda status: status.getConnection().createQuery("from Product p where p.name = :name", Product)
            .setParameter("name", name)
            .getSingleResult()
        )
