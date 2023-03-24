package example.domain;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Collections;
import java.util.List;

@Serdeable
@MappedEntity
public record Student(
        @Id
        @GeneratedValue(GeneratedValue.Type.AUTO)
        Long id,
        String name,
        //@JoinTable(name = "STUDENT_CLASSES")
        @Relation(Relation.Kind.MANY_TO_MANY)
        List<Class> classes
) {
    public Student(String name) {
        this(null, name, Collections.emptyList());
    }
}
