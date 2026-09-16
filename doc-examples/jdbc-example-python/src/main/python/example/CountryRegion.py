from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Relation
from micronaut.data.model.naming import NamingStrategies

from example.Country import Country


# tag::namingStrategy[]
@MappedEntity(namingStrategy=NamingStrategies.Raw)
@dataclass
class CountryRegion:
    # end::namingStrategy[]
    name: str
    country: Annotated[Country | None, Relation("MANY_TO_ONE")] = None
    id: Annotated[int | None, Id, GeneratedValue] = None
    # tag::namingStrategy[]
    # ...
# end::namingStrategy[]
