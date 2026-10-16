# tag::transfer[]
from dataclasses import dataclass
from typing import TYPE_CHECKING, Annotated

from java.math import BigDecimal
from micronaut.data.graph.annotation import RelationshipId, RelationshipProperties, TargetNode

if TYPE_CHECKING:
    from example.Account import Account


@RelationshipProperties
@dataclass
class Transfer:
    amount: BigDecimal
    to: Annotated["Account | None", TargetNode] = None
    id: Annotated[int | None, RelationshipId] = None
# end::transfer[]
