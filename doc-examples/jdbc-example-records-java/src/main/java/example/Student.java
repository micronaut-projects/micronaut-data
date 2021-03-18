package example;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@MappedEntity
public record Student(@Id @GeneratedValue @Nullable
                      Long id,
                      String name,
                      @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = Relation.Cascade.PERSIST)
                      List<Course> courses,
                      @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "student")
                      Set<CourseRating> ratings) {

    public Student(String name, List<Course> courses) {
        this(null, name, courses, Collections.emptySet());
    }

}
