package io.micronaut.data.jdbc.h2.remap;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.DataType;

import java.util.List;

import static io.micronaut.data.annotation.Relation.Cascade.ALL;
import static io.micronaut.data.annotation.Relation.Kind.MANY_TO_MANY;

@MappedEntity("att_student")
record Student(

    @Id
    @MappedProperty(converter = StudentIdAttributeConverter.class, type = DataType.UUID)
    StudentId id,

    String name,

    @Relation(value = MANY_TO_MANY, cascade = ALL)
    List<Course> courses
) {
}
