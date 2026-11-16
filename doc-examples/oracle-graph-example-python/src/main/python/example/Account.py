# tag::account[]
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Annotated

from micronaut.data.annotation import Id
from micronaut.data.graph.annotation import Node, Relationship

if TYPE_CHECKING:
    from example.Transfer import Transfer


@Node(value="accounts", labels=["Account"])  # <1>
@dataclass
class Account:
    id: Annotated[int, Id]  # <2>
    name: str
    transfers: Annotated["list[Transfer]", Relationship("TRANSFERS")] = field(default_factory=list)  # <3>
# end::account[]
