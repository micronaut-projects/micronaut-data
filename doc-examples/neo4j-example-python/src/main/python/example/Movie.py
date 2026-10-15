# tag::movie[]
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Annotated

from micronaut.data.annotation import Id, MappedProperty
from micronaut.data.graph.annotation import Node, Relationship

if TYPE_CHECKING:
    from example.Person import Person
    from example.Role import Role


@Node  # <1>
@dataclass
class Movie:
    title: Annotated[str, Id]  # <2>
    description: Annotated[str | None, MappedProperty("tagline")] = None  # <3>
    released: int | None = None
    roles: Annotated["list[Role]", Relationship(type="ACTED_IN", direction="INCOMING")] = field(default_factory=list)  # <4>
    directors: Annotated["list[Person]", Relationship(type="DIRECTED", direction="INCOMING")] = field(default_factory=list)
# end::movie[]
