from jakarta.inject import Singleton
from micronaut.data.annotation import Repository
from micronaut.data.repository import CrudRepository

from example.Manufacturer import Manufacturer


# tag::singleton[]
@Singleton
@Repository
class SingletonManufacturerRepository(CrudRepository[Manufacturer, int]):
    pass
# end::singleton[]
