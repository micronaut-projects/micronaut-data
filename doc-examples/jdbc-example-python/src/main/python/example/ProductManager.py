from jakarta.inject import Singleton
from java.sql import Connection
from micronaut.transaction import TransactionOperations

from example.Manufacturer import Manufacturer
from example.Product import Product
from example.ProductRepository import ProductRepository


@Singleton
class ProductManager:
    def __init__(self,
                 connection: Connection,
                 transaction_manager: TransactionOperations[Connection],  # <1>
                 product_repository: ProductRepository):
        self.connection = connection
        self.transaction_manager = transaction_manager
        self.product_repository = product_repository

    def save(self, name: str, manufacturer: Manufacturer) -> Product:
        def insert(status):  # <2>
            product = Product(name, manufacturer)
            ps = self.connection.prepareStatement("insert into product (name, manufacturer_id) values (?, ?)")
            try:
                ps.setString(1, name)
                ps.setLong(2, manufacturer.id)
                ps.execute()
            finally:
                ps.close()
            return product
        return self.transaction_manager.executeWrite(insert)

    def find(self, name: str) -> Product | None:
        def select(status):  # <3>
            ps = status.getConnection().prepareStatement("select * from product p where p.name = ?")
            try:
                ps.setString(1, name)
                rs = ps.executeQuery()
                try:
                    if rs.next():
                        return Product(rs.getString("name"), None)
                    return None
                finally:
                    rs.close()
            finally:
                ps.close()
        return self.transaction_manager.executeRead(select)

    def save_using_repo(self, name: str, manufacturer: Manufacturer) -> Product:
        """Creates new product using transaction operations and product repository."""
        return self.transaction_manager.executeWrite(  # <4>
            lambda status: self.product_repository.save(Product(name, manufacturer))
        )

    def find_using_repo(self, name: str) -> Product | None:
        """Finds product by name using transaction manager and product repository."""
        return self.transaction_manager.executeRead(  # <5>
            lambda status: self.product_repository.findByName(name).orElse(None)
        )
