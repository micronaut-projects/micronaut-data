# tag::relations[]
from dataclasses import dataclass, field
from typing import Annotated

from java.util import Date
from micronaut.data.annotation import Id, MappedEntity, Relation
from micronaut.data.cosmos.annotation import ETag, PartitionKey

from example.Address import Address
from example.Child import Child


@MappedEntity
@dataclass
class Family:
    id: Annotated[str | None, Id] = None
    lastName: Annotated[str | None, PartitionKey] = None
    address: Annotated[Address | None, Relation("EMBEDDED")] = None
    children: Annotated[list[Child], Relation("ONE_TO_MANY")] = field(default_factory=list)
    # end::relations[]
    # ...
    registered: bool = False
    registeredDate: Date | None = None
    tags: list[str] | None = None
    # tag::locking[]
    documentVersion: Annotated[str | None, ETag] = None
    # end::locking[]
