package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.List;

/**
 * The inverse side of the many-to-many that {@link Member} owns, so the join table is defined by {@link Member#clubs()}.
 */
@MappedEntity(value = "TBL_CLUB", alias = "cl")
public record Club(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,
    String name,
    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "clubs")
    List<Member> members
) {}
