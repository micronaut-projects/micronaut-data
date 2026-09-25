from typing import TYPE_CHECKING

from jakarta.inject import Singleton
from micronaut.core.convert import ConversionContext
from micronaut.data.model.runtime.convert import AttributeConverter

if TYPE_CHECKING:
    from example.Quantity import Quantity


@Singleton  # <1>
class QuantityAttributeConverter(AttributeConverter["Quantity", int]):

    def convertToPersistedValue(self, quantity: "Quantity | None", context: ConversionContext) -> int | None:  # <2>
        return None if quantity is None else quantity.amount

    def convertToEntityValue(self, value: int | None, context: ConversionContext) -> "Quantity | None":  # <3>
        from example.Quantity import Quantity
        return None if value is None else Quantity(value)
