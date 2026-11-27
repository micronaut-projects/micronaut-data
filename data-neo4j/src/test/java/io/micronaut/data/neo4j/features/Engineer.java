package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node
public class Engineer {
    @Id
    private final String name;
    @Relationship("ASSIGNED_TO")
    private Assignment assignment;
    @Relationship("LAST_REVIEWED")
    private Review lastReview;
    @Relationship("REVIEWED")
    private List<Review> reviews;
    @Relationship("AUDITED")
    private List<Audit> audits;
    @Relationship("STAFFED")
    private List<Assignment> assignments;

    public Engineer(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Assignment getAssignment() {
        return assignment;
    }

    public void setAssignment(Assignment assignment) {
        this.assignment = assignment;
    }

    public Review getLastReview() {
        return lastReview;
    }

    public void setLastReview(Review lastReview) {
        this.lastReview = lastReview;
    }

    public List<Review> getReviews() {
        return reviews;
    }

    public void setReviews(List<Review> reviews) {
        this.reviews = reviews;
    }

    public List<Assignment> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<Assignment> assignments) {
        this.assignments = assignments;
    }

    public List<Audit> getAudits() {
        return audits;
    }

    public void setAudits(List<Audit> audits) {
        this.audits = audits;
    }
}
