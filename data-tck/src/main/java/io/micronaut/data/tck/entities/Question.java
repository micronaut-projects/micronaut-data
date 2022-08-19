package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

/**
 * The question mapping entity.
 */
@MappedEntity("questions")
public class Question {

    @Nullable
    @GeneratedValue
    @Id
    @MappedProperty("id")
    private Integer internalId;

    @Relation(Relation.Kind.EMBEDDED)
    private QuestionId id;

    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getInternalId() {
        return internalId;
    }

    public void setInternalId(@Nullable Integer internalId) {
        this.internalId = internalId;
    }

    public QuestionId getId() {
        return id;
    }

    public void setId(QuestionId id) {
        this.id = id;
    }
}
