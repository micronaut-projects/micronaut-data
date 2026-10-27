from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from micronaut.data.annotation.search import SearchField, Searchable


# tag::entity[]
@MappedEntity("books")
@Searchable(language="english")  # <1>
@dataclass(frozen=True)
class Book:
    id: Annotated[str | None, Id, GeneratedValue]
    title: Annotated[str, SearchField(weight="HIGH")]  # <2>
    description: Annotated[str, SearchField]  # <3>
    author: str
# end::entity[]
