
package example

import io.micronaut.context.annotation.Factory
import io.micronaut.core.convert.TypeConverter

import javax.inject.Singleton
import java.util.Optional

@Factory // <1>
class QuantityConverters {

    @Singleton // <2>
    fun quantityIntegerTypeConverter(): TypeConverter<Quantity, Int> {
        return TypeConverter { quantity, targetType, context -> Optional.of<Int>(quantity.amount) }
    }

    @Singleton // <3>
    fun integerQuantityTypeConverter(): TypeConverter<Int, Quantity> {
        return TypeConverter { integer, targetType, context -> Optional.of<Quantity>(Quantity(integer)) }
    }
}
