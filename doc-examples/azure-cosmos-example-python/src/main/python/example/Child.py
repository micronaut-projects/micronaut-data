from dataclasses import dataclass, field
from typing import Annotated

from micronaut.data.annotation import Relation
from micronaut.serde.annotation import Serdeable

from example.GenderAware import GenderAware
from example.Pet import Pet


@Serdeable
@dataclass
class Child(GenderAware):
    firstName: str | None = None
    grade: int = 0
    pets: Annotated[list[Pet], Relation("ONE_TO_MANY")] = field(default_factory=list)
