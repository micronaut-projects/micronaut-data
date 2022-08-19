package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Where;

import java.util.List;

/**
 * The template mapping entity.
 */
@MappedEntity("templates")
@Where("@.enabled = true")
public class Template {

    @Id
    @GeneratedValue
    private Long id;

    private boolean enabled;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "id.template", cascade = Relation.Cascade.ALL)
    private List<Question> questions;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public void setQuestions(List<Question> questions) {
        this.questions = questions;
    }
}
