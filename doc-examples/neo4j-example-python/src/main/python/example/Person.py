# tag::person[]
from dataclasses import dataclass, field
from typing import Annotated

from java.util import UUID
from micronaut.data.annotation import AutoPopulated, Id
from micronaut.data.graph.annotation import Node, Relationship


@Node
@dataclass
class Person:
    name: str
    born: int | None = None
    follows: Annotated["list[Person]", Relationship("FOLLOWS")] = field(default_factory=list)  # <2>
    id: Annotated[UUID | None, Id, AutoPopulated] = None  # <1>
# end::person[]
