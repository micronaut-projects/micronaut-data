package example;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;

@Singleton // <1>
public class ItemPriceAttributeConverter implements AttributeConverter<ItemPrice, Double> {

    @Override // <2>
    public Double convertToPersistedValue(ItemPrice bookPrice, ConversionContext context) {
        return bookPrice == null ? null : bookPrice.getPrice();
    }

    @Override // <3>
    public ItemPrice convertToEntityValue(Double value, ConversionContext context) {
        return value == null ? null : ItemPrice.valueOf(value);
    }

}
