package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity(value = "TBL_STUDENT_CLASSES", alias = "sc")
class StudentClass {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id
}
