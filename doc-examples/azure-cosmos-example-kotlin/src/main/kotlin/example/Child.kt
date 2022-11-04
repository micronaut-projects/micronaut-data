package example

import io.micronaut.data.annotation.Relation
import io.micronaut.serde.annotation.Serdeable
import java.util.ArrayList

@Serdeable
class Child {
    var firstName: String? = null
    var gender: String? = null
    var grade = 0

    @Relation(value = Relation.Kind.ONE_TO_MANY)
    var pets: List<Pet> = ArrayList()
}
