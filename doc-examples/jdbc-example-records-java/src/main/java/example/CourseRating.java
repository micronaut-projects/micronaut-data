package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity
public record CourseRating(@Id @GeneratedValue @Nullable
                           Long id,
                           @Relation(Relation.Kind.MANY_TO_ONE)
                           Student student,
                           @Relation(Relation.Kind.MANY_TO_ONE)
                           Course course,
                           int rating) {

    public CourseRating(Student student, Course course, int rating) {
        this(null, student, course, rating);
    }

}