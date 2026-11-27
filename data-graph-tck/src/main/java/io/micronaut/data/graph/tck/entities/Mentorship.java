package io.micronaut.data.graph.tck.entities;

import io.micronaut.data.graph.annotation.RelationshipId;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.annotation.TargetNode;

/**
 * A relationship entity of its own for {@link Colleague#getMentor()}: on Oracle a relationship entity is the edge table
 * of one relationship type, so it cannot be shared with {@link Collaboration}.
 */
@RelationshipProperties
public class Mentorship {
    @RelationshipId
    private Long id;
    private final String project;
    @TargetNode
    private final Colleague colleague;

    public Mentorship(String project, Colleague colleague) {
        this.project = project;
        this.colleague = colleague;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProject() {
        return project;
    }

    public Colleague getColleague() {
        return colleague;
    }
}
