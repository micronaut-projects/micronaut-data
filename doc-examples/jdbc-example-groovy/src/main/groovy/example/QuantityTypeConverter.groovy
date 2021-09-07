
package example

import groovy.transform.CompileStatic
import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.model.runtime.convert.TypeConverter
import jakarta.inject.Singleton

@Singleton // <1>
@CompileStatic
class QuantityTypeConverter implements TypeConverter<Quantity, Integer> {

    @Override // <2>
    Integer convertToPersistedValue(Quantity quantity, ConversionContext context) {
        return quantity == null ? null : quantity.getAmount()
    }

    @Override // <3>
    Quantity convertToEntityValue(Integer value, ConversionContext context) {
        return value == null ? null : new Quantity(value)
    }

}
