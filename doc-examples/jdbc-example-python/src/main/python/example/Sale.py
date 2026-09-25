from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Relation

from example.Product import Product
from example.Quantity import Quantity


@MappedEntity
@dataclass
class Sale:
    product: Annotated[Product, Relation(value="MANY_TO_ONE")]
    quantity: Quantity
    id: Annotated[int | None, Id, GeneratedValue] = None
