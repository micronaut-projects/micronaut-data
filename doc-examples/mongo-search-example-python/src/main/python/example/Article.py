from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from micronaut.data.annotation.search import SearchField, Searchable
from micronaut.data.mongodb.annotation import MongoSearchIndex


# tag::entity[]
@MappedEntity
@Searchable(language="english")  # <1>
@MongoSearchIndex(name="articles")  # <2>
@dataclass(frozen=True)
class Article:
    id: Annotated[str | None, Id, GeneratedValue]
    title: Annotated[str, SearchField(weight="HIGH")]  # <3>
    body: Annotated[str, SearchField]
    author: str
# end::entity[]
