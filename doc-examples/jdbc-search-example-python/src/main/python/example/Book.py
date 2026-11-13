from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from jakarta.persistence import Column
from micronaut.data.annotation.search import SearchField, Searchable
from micronaut.data.model.vector import Vector


# tag::entity[]
@MappedEntity
@Searchable(language="english")  # <1>
@dataclass(frozen=True)
class Book:
    id: Annotated[int | None, Id, GeneratedValue]
    title: Annotated[str, SearchField(weight="HIGH")]  # <2>
    description: Annotated[str, SearchField]  # <3>
    author: str
    embedding: Annotated[Vector | None, Column(length=3)]
# end::entity[]
