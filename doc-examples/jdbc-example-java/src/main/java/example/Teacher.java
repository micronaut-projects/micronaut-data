package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity(value = "TBL_TEACHER", alias = "t")
public class Teacher {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
}
