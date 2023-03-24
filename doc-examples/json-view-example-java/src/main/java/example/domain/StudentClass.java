package example.domain;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@MappedEntity("STUDENT_CLASSES")
public record StudentClass(
        @Id
        @GeneratedValue(GeneratedValue.Type.AUTO)
        Long id,
        @Relation(Relation.Kind.MANY_TO_ONE)
        Student student,
        @Relation(Relation.Kind.MANY_TO_ONE)
        @MappedProperty("CLASS_ID")
        Class clazz
) {

    public StudentClass(Student student, Class clazz) {
        this(null, student, clazz);
    }
}
