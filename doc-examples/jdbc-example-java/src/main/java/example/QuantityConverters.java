package example;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.convert.TypeConverter;

import javax.inject.Singleton;
import java.util.Optional;

@Factory // <1>
public class QuantityConverters {

    @Singleton // <2>
    TypeConverter<Quantity, Integer> quantityIntegerTypeConverter() {
        return (object, targetType, context) -> Optional.of(object.getAmount());
    }

    @Singleton // <3>
    TypeConverter<Integer, Quantity> integerQuantityTypeConverter() {
        return (object, targetType, context) -> Optional.of(Quantity.valueOf(object));
    }
}
