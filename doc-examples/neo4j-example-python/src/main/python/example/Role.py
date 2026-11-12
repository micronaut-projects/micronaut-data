# tag::role[]
from dataclasses import dataclass
from typing import Annotated

from micronaut.data.graph.annotation import RelationshipId, RelationshipProperties, TargetNode

from example.Person import Person


@RelationshipProperties  # <1>
@dataclass
class Role:
    roles: list[str]  # <3>
    person: Annotated[Person, TargetNode]  # <4>
    id: Annotated[int | None, RelationshipId] = None  # <2>
# end::role[]
