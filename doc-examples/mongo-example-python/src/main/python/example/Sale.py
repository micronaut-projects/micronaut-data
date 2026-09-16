from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Relation
from org.bson.types import ObjectId

from example.Product import Product
from example.Quantity import Quantity


@MappedEntity
@dataclass
class Sale:
    product: Annotated[Product, Relation("MANY_TO_ONE")]
    # TODO(python): the converter member overflows the compiler (MappedPropertyMapper re-enters the class element registry)
    # quantity: Annotated[Quantity, MappedProperty(converter=QuantityAttributeConverter)]
    quantity: Quantity
    id: Annotated[ObjectId | None, Id, GeneratedValue] = None
