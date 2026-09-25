from dataclasses import dataclass

from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class BookDTO:
    title: str | None = None
    pages: int = 0
