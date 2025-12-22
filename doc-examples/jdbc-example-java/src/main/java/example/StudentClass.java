package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity(value = "TBL_STUDENT_CLASSES", alias = "sc")
public class StudentClass {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
}
