from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import Duplicated, Id, MappedEntity


# tag::duplicated[]
@Duplicated(tablespace="PLANS_TSP")
@MappedEntity
@dataclass
class Plan:
    code: Annotated[str, Id]
    name: str
# end::duplicated[]
