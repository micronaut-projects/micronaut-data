# tag::book[]
from dataclasses import dataclass
from typing import Annotated

from java.util import Date
from micronaut.data.annotation import DateCreated, DateUpdated, GeneratedValue, Id, MappedEntity, MappedProperty
from micronaut.data.cosmos.annotation import PartitionKey

from example.ItemPrice import ItemPrice
from example.ItemPriceAttributeConverter import ItemPriceAttributeConverter


@MappedEntity
@dataclass
class Book:
    title: str
    pages: int
    itemPrice: Annotated[ItemPrice | None, MappedProperty(converter=ItemPriceAttributeConverter)] = None
    createdDate: Annotated[Date | None, DateCreated] = None
    updatedDate: Annotated[Date | None, DateUpdated] = None
    id: Annotated[str | None, Id, GeneratedValue, PartitionKey] = None
    # end::book[]
    # tag::book[]
    # ...
# end::book[]
