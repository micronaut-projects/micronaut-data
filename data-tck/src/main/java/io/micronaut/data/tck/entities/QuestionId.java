package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

/**
 * The question id embeddable composite id for {@link Question} mapping.
 */
@Embeddable
public class QuestionId {

    @Nullable
    @Relation(Relation.Kind.MANY_TO_ONE)
    @MappedProperty("template_id")
    private Template template;

    @MappedProperty("number")
    private Integer number;

    @Nullable
    public Template getTemplate() {
        return template;
    }

    public void setTemplate(@Nullable Template template) {
        this.template = template;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }
}
