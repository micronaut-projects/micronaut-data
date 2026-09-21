from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, MappedProperty, Relation
from org.bson.types import ObjectId

from example.Product import Product
from example.Quantity import Quantity
from example.QuantityAttributeConverter import QuantityAttributeConverter


@MappedEntity
@dataclass
class Sale:
    product: Annotated[Product, Relation("MANY_TO_ONE")]
    quantity: Annotated[Quantity, MappedProperty(converter=QuantityAttributeConverter)]
    id: Annotated[ObjectId | None, Id, GeneratedValue] = None
