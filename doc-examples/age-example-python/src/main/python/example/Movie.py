# tag::movie[]
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Annotated

from micronaut.data.annotation import Id
from micronaut.data.graph.annotation import Node, Relationship

if TYPE_CHECKING:
    from example.Person import Person


@Node
@dataclass
class Movie:
    title: Annotated[str, Id]
    released: int | None = None
    directors: Annotated["list[Person]", Relationship(type="DIRECTED", direction="INCOMING")] = field(default_factory=list)  # <1>
# end::movie[]
