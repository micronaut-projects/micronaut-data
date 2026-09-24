from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Relation

from example.Book import Book


@MappedEntity
@dataclass
class Review:
    """A book review."""
    reviewer: str
    content: str
    book: Annotated[Book | None, Relation(value="MANY_TO_ONE")] = None
    id: Annotated[int | None, Id, GeneratedValue] = None
