# tag::student[]
from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Version
from org.bson.types import ObjectId


@MappedEntity
@dataclass
class Student:
    id: Annotated[ObjectId | None, Id, GeneratedValue] = None
    version: Annotated[int | None, Version] = None
    # end::student[]
    name: str | None = None
