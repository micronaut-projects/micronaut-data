from dataclasses import dataclass

from micronaut.data.annotation import Embeddable
from micronaut.serde.annotation import Serdeable


@Serdeable
@Embeddable
@dataclass
class Address:
    state: str | None = None
    county: str | None = None
    city: str | None = None
