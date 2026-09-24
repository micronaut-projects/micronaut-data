from dataclasses import dataclass

from micronaut.serde.annotation import Serdeable


@Serdeable
@dataclass
class BookDTO:
    title: str | None = None
    pages: int = 0
