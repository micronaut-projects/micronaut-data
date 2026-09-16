from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Relation

if TYPE_CHECKING:
    from example.Review import Review


@MappedEntity
@dataclass
class Book:
    title: str
    pages: int
    reviews: Annotated["list[Review]", Relation(value="ONE_TO_MANY", mappedBy="book", cascade="ALL")] = field(default_factory=list)
    id: Annotated[int | None, Id, GeneratedValue] = None
