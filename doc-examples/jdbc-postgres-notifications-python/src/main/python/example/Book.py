from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import Id, MappedEntity
from micronaut.serde.annotation import Serdeable


# tag::book[]
@Serdeable
@MappedEntity(value="book", schema="public")
@dataclass(frozen=True)
class Book:
    id: Annotated[int, Id]
    title: str
# end::book[]
