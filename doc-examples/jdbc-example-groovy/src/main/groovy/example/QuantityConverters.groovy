package example

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Factory
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.TypeConverter

import javax.inject.Singleton

@Factory // <1>
@CompileStatic
class QuantityConverters {

    @Singleton // <2>
    TypeConverter<Quantity, Integer> quantityIntegerTypeConverter() {
        return { Quantity quantity, Class targetType, ConversionContext context ->
            Optional.of(quantity.amount)
        } as TypeConverter<Quantity, Integer>
    }

    @Singleton // <3>
    TypeConverter<Integer, Quantity> integerQuantityTypeConverter() {
        return { Integer integer, Class targetType, ConversionContext context ->
            Optional.of(new Quantity(integer))
        } as TypeConverter<Integer, Quantity>
    }
}
