
package example;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.TypeConverter;
import jakarta.inject.Singleton;

@Singleton // <1>
public class QuantityTypeConverter implements TypeConverter<Quantity, Integer> {

    @Override // <2>
    public Integer convertToPersistedValue(Quantity quantity, ConversionContext context) {
        return quantity == null ? null : quantity.getAmount();
    }

    @Override // <3>
    public Quantity convertToEntityValue(Integer value, ConversionContext context) {
        return value == null ? null : Quantity.valueOf(value);
    }

}
