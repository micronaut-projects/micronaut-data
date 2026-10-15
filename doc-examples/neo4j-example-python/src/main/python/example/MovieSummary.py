from dataclasses import dataclass

from micronaut.core.annotation import Introspected


# tag::dto[]
@Introspected
@dataclass
class MovieSummary:
    title: str
    released: int | None
# end::dto[]
