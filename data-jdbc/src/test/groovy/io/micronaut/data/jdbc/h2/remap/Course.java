package io.micronaut.data.jdbc.h2.remap;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.List;
import java.util.UUID;

import static io.micronaut.data.annotation.Relation.Cascade.ALL;
import static io.micronaut.data.annotation.Relation.Kind.MANY_TO_MANY;

@MappedEntity("att_course")
record Course(

    @Id
    UUID id,

    String name,

    @Relation(value = MANY_TO_MANY, mappedBy = "courses", cascade = ALL)
    List<Student> students
) {
}
