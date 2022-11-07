package example;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;

@Singleton
public class ItemPriceAttributeConverter implements AttributeConverter<ItemPrice, Double> {

    @Override
    public Double convertToPersistedValue(ItemPrice bookPrice, ConversionContext context) {
        return bookPrice == null ? null : bookPrice.getPrice();
    }

    @Override
    public ItemPrice convertToEntityValue(Double value, ConversionContext context) {
        return value == null ? null : ItemPrice.valueOf(value);
    }

}
