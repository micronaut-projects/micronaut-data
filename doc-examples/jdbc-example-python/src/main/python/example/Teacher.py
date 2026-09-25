from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity


@MappedEntity(value="TBL_TEACHER", alias="t")
@dataclass
class Teacher:
    id: Annotated[int | None, Id, GeneratedValue("IDENTITY")] = None
