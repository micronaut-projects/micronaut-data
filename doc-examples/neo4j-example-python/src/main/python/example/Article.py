# tag::article[]
from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import Id, Index, Version
from micronaut.data.graph.annotation import Node


@Node
@Index(columns=["author"])  # <1>
@dataclass
class Article:
    slug: Annotated[str, Id]  # <2>
    title: str
    author: str
    version: Annotated[int | None, Version] = None  # <3>
# end::article[]
