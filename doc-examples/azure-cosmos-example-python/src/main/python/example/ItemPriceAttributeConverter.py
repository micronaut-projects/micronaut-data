from jakarta.inject import Singleton
from micronaut.core.convert import ConversionContext
from micronaut.data.model.runtime.convert import AttributeConverter

from example.ItemPrice import ItemPrice


@Singleton
class ItemPriceAttributeConverter(AttributeConverter[ItemPrice, float]):

    def convertToPersistedValue(self, book_price: ItemPrice | None, context: ConversionContext) -> float | None:
        return None if book_price is None else book_price.price

    def convertToEntityValue(self, value: float | None, context: ConversionContext) -> ItemPrice | None:
        return None if value is None else ItemPrice(value)
