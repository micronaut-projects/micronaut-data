from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity


@MappedEntity(value="TBL_STUDENT", alias="s")
@dataclass
class Student:
    name: str
    id: Annotated[int | None, Id, GeneratedValue("IDENTITY")] = None
