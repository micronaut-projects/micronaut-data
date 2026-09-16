# tag::book[]
from dataclasses import dataclass
from typing import Annotated

from java.util import Date
from micronaut.data.annotation import DateCreated, DateUpdated, GeneratedValue, Id, MappedEntity
from micronaut.data.cosmos.annotation import PartitionKey

from example.ItemPrice import ItemPrice


@MappedEntity
@dataclass
class Book:
    title: str
    pages: int
    # TODO(python): the converter member overflows the compiler (MappedPropertyMapper re-enters the class element registry)
    # itemPrice: Annotated[ItemPrice | None, MappedProperty(converter=ItemPriceAttributeConverter)] = None
    itemPrice: ItemPrice | None = None
    createdDate: Annotated[Date | None, DateCreated] = None
    updatedDate: Annotated[Date | None, DateUpdated] = None
    id: Annotated[str | None, Id, GeneratedValue, PartitionKey] = None
    # end::book[]
    # tag::book[]
    # ...
# end::book[]
