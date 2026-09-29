package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity(value = "TBL_STUDENT", alias = "s")
class Student {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id
    String name
}
