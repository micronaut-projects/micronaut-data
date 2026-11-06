# tag::person[]
from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import Id
from micronaut.data.graph.annotation import Node


@Node  # <1>
@dataclass
class Person:
    name: Annotated[str, Id]  # <2>
    born: int | None = None
# end::person[]
