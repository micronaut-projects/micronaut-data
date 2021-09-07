
package example

import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.model.DataType

@TypeDef(type = DataType.INTEGER, converter = QuantityTypeConverter::class)
data class Quantity(val amount: Int)
