from dataclasses import dataclass

from micronaut.data.annotation import TypeDef

from example.QuantityAttributeConverter import QuantityAttributeConverter


@TypeDef(type="INTEGER", converter=QuantityAttributeConverter)
@dataclass(frozen=True)
class Quantity:
    amount: int
