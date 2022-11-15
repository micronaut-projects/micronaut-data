
package example

import groovy.transform.CompileStatic
import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.model.runtime.convert.AttributeConverter
import jakarta.inject.Singleton

@Singleton // <1>
@CompileStatic
class ItemPriceAttributeConverter implements AttributeConverter<ItemPrice, Double> {

    @Override // <2>
    Double convertToPersistedValue(ItemPrice itemPrice, ConversionContext context) {
        return itemPrice == null ? null : itemPrice.getPrice()
    }

    @Override // <3>
    ItemPrice convertToEntityValue(Double value, ConversionContext context) {
        return value == null ? null : new ItemPrice(value)
    }

}
