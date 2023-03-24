package example.domain;

import example.domain.view.StudentView;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.jdbc.annotation.JoinTable;
import io.micronaut.data.model.DataType;
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
        @JoinTable(name = "STUDENT_CLASSES")
        @Relation(Relation.Kind.MANY_TO_MANY)
        @Nullable
        List<Class> classes
) {
    public Student(String name) {
        this(null, name, Collections.emptyList());
    }
}
