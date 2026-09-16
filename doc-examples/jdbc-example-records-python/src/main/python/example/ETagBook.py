# tag::generated-etag[]
from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import Embeddable, GeneratedValue, Id, MappedEntity, Relation
from micronaut.data.annotation.sql import ETagValue, ETaggable, GeneratedETag


@Embeddable
@dataclass(frozen=True)
class BookDetails:
    pages: int
    chapters: Annotated[int, ETagValue(exclude=True)]


@MappedEntity("etag_book")
@ETaggable
@dataclass(frozen=True)
class ETagBook:
    id: Annotated[int | None, Id, GeneratedValue]
    title: str
    bookDetails: Annotated[BookDetails, Relation("EMBEDDED")]
    etag: Annotated[str | None, GeneratedETag]
# end::generated-etag[]
