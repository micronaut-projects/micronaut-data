from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Annotated
from uuid import UUID

from micronaut.data.annotation import AutoPopulated, Id, MappedEntity, Relation

if TYPE_CHECKING:
    from example.CountryRegion import CountryRegion


# tag::country[]
@MappedEntity
@dataclass
class Country:
    name: str
    regions: Annotated["set[CountryRegion]", Relation(value="ONE_TO_MANY", mappedBy="country")] = field(default_factory=set)
    uuid: Annotated[UUID | None, Id, AutoPopulated] = None
# end::country[]
