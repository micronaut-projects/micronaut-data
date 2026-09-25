from dataclasses import dataclass
from typing import Annotated

from java.util import Date
from micronaut.data.annotation import DateCreated, GeneratedValue, Id, MappedEntity


@MappedEntity  # <1>
@dataclass(frozen=True)
class Book:
    id: Annotated[int | None, Id, GeneratedValue]  # <2>
    dateCreated: Annotated[Date | None, DateCreated]
    title: str
    pages: int
