# tag::pet[]
from dataclasses import dataclass, field
from typing import Annotated

from micronaut.data.annotation import Id
from micronaut.data.graph.annotation import DynamicAttributes, DynamicRelationships, Node


@Node
@dataclass
class Pet:
    name: Annotated[str, Id]
    attributes: Annotated[dict[str, object], DynamicAttributes] = field(default_factory=dict)  # <1>
    # end::pet[]
    # tag::relationships[]
    related: Annotated[dict[str, list[object]], DynamicRelationships] = field(default_factory=dict)  # <1>
    # end::relationships[]
