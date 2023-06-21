package example

import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.model.runtime.convert.AttributeConverter
import jakarta.inject.Singleton

@Singleton // <1>
class QuantityAttributeConverter : AttributeConverter<Quantity?, Int?> {

    // <2>
    override fun convertToPersistedValue(quantity: Quantity?, context: ConversionContext): Int? {
        return quantity?.amount
    }

    // <3>
    override fun convertToEntityValue(value: Int?, context: ConversionContext): Quantity? {
        return if (value == null) null else Quantity(value)
    }

}