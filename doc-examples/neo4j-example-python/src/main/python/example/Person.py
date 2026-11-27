# tag::person[]
from dataclasses import dataclass, field
from typing import Annotated

from java.util import UUID
from micronaut.data.annotation import AutoPopulated, Id
from micronaut.data.graph.annotation import Labels, Node, Relationship


@Node
@dataclass
class Person:
    name: str
    born: int | None = None
    follows: Annotated["list[Person]", Relationship("FOLLOWS")] = field(default_factory=list)  # <2>
    id: Annotated[UUID | None, Id, AutoPopulated] = None  # <1>
    # end::person[]
    # tag::labels[]
    labels: Annotated[list[str], Labels] = field(default_factory=list)  # <1>
    # end::labels[]
# tag::person[]
# end::person[]
