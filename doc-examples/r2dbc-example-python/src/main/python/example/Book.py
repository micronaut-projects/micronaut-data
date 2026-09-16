from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Relation
from micronaut.serde.annotation import Serdeable

from example.Author import Author


@Serdeable
@MappedEntity
@dataclass
class Book:
    title: str
    pages: int
    author: Annotated[Author | None, Relation("MANY_TO_ONE")] = None
    id: Annotated[int | None, Id, GeneratedValue] = None
