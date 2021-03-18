package example;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.Collections;
import java.util.List;

@MappedEntity
public record Course(@Id @GeneratedValue @Nullable
                     Long id,
                     String name,
                     @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "courses")
                     List<Student> students) {

    public Course(String name) {
        this(null, name, Collections.emptyList());
    }

}
