
package example

import groovy.transform.Immutable
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.model.DataType

@TypeDef(type = DataType.INTEGER, converter = QuantityAttributeConverter.class)
@Immutable
class Quantity {
    int amount
}
