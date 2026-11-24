package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.naming.NamingStrategies;

import java.util.List;

/**
 * Owns the many-to-many with {@link Club}. It uses a different naming strategy than {@link Club}, so its join table
 * is named by this strategy: {@code memberclub} with the columns {@code memberid} and {@code clubid}.
 */
@MappedEntity(value = "TBL_MEMBER", alias = "mbr", namingStrategy = NamingStrategies.LowerCase.class)
public record Member(
    @Id
    Long id,
    String name,
    @Relation(Relation.Kind.MANY_TO_MANY)
    List<Club> clubs
) {}
