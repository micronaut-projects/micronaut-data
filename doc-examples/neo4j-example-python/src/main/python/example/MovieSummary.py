from dataclasses import dataclass

from micronaut.serde.annotation import Serdeable


# tag::dto[]
@Serdeable
@dataclass
class MovieSummary:
    title: str
    released: int | None
# end::dto[]
