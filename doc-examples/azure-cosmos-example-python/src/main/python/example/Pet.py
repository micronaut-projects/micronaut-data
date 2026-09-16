from dataclasses import dataclass
from enum import Enum

from micronaut.serde.annotation import Serdeable


class PetType(Enum):
    DOG = "DOG"
    CAT = "CAT"
    HAMSTER = "HAMSTER"


@Serdeable
@dataclass
class Pet:
    givenName: str | None = None
    type: PetType | None = None
