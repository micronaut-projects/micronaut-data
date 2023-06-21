package example

import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.model.runtime.convert.AttributeConverter
import jakarta.inject.Singleton

@Singleton // <1>
class ItemPriceAttributeConverter : AttributeConverter<ItemPrice?, Double?> {

    // <2>
    override fun convertToPersistedValue(itemPrice: ItemPrice?, context: ConversionContext): Double? {
        return itemPrice?.price
    }

    // <3>
    override fun convertToEntityValue(value: Double?, context: ConversionContext): ItemPrice? {
        return if (value == null) null else ItemPrice(value)
    }

}
