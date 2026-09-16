# tag::book[]
from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from org.bson.types import ObjectId


@MappedEntity
@dataclass
class Book:
    title: str
    pages: int
    id: Annotated[ObjectId | None, Id, GeneratedValue] = None
    # end::book[]
    # tag::book[]
    # ...
# end::book[]
