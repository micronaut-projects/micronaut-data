package io.micronaut.data.oracle.graph.r2dbc.invalid;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;
import io.micronaut.data.graph.annotation.RelationshipId;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.annotation.TargetNode;

import java.util.List;

/**
 * Mappings the schema generator rejects, as nothing in a row of a shared edge table tells the relationships apart.
 */
public interface InvalidEdges {

    @Node("actors")
    record Actor(@Id Long id,
                 @Relationship("ACTED_IN") List<Credit> acted,
                 @Relationship("DIRECTED") List<Credit> directed) {
    }

    @RelationshipProperties
    @MappedEntity("credit")
    record Credit(@RelationshipId Long id, @TargetNode Show show) {
    }

    @Node("shows")
    record Show(@Id Long id) {
    }

    @Node("pens")
    record Pen(@Id Long id,
               @Relationship("LIKES") List<Ink> liked,
               @Relationship(type = "LIKES", direction = Direction.INCOMING) List<Ink> likedBy) {
    }

    @Node("inks")
    record Ink(@Id Long id) {
    }

    @Node("readers")
    record Reader(@Id Long id, @Relationship("READ") List<ReaderLoan> loans) {
    }

    @RelationshipProperties
    @MappedEntity("reader_loan")
    record ReaderLoan(@RelationshipId Long id, @TargetNode Title title) {
    }

    @Node("titles")
    record Title(@Id Long id, @Relationship(type = "READ", direction = Direction.INCOMING) List<TitleLoan> loans) {
    }

    @RelationshipProperties
    @MappedEntity("title_loan")
    record TitleLoan(@RelationshipId Long id, @TargetNode Reader reader) {
    }
}
