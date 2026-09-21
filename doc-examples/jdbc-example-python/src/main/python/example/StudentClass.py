from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity


@MappedEntity(value="TBL_STUDENT_CLASSES", alias="sc")
@dataclass
class StudentClass:
    id: Annotated[int | None, Id, GeneratedValue("IDENTITY")] = None
