# tag::country[]
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Annotated

from micronaut.data.annotation import Id, MappedEntity, Relation
from org.bson.types import ObjectId

if TYPE_CHECKING:
    from example.CountryRegion import CountryRegion


@MappedEntity  # <1>
@dataclass
class Country:
    name: str  # <4>
    regions: Annotated["set[CountryRegion]", Relation(value="ONE_TO_MANY", mappedBy="country")] = field(default_factory=set)  # <3>
    id: Annotated[ObjectId | None, Id] = None  # <2>
    # end::country[]
    # tag::country[]
    # ...
# end::country[]
