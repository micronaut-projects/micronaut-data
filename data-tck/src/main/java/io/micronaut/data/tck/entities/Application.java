package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

/**
 * The application mapping entity.
 */
@MappedEntity("applications")
public class Application {

    @GeneratedValue
    @Id
    private Long id;

    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private Template template;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Template getTemplate() {
        return template;
    }

    public void setTemplate(Template template) {
        this.template = template;
    }
}
