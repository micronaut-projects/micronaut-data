from micronaut.data.annotation import Repository
from micronaut.data.repository import CrudRepository

from example.Manufacturer import Manufacturer


@Repository
class ManufacturerRepository(CrudRepository[Manufacturer, int]):
    def save(self, name: str) -> Manufacturer: ...
