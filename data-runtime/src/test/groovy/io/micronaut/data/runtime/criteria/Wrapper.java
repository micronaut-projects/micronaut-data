package io.micronaut.data.runtime.criteria;

import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

/**
 * An identity-less entity, stored inline in the owning entity.
 */
@MappedEntity
public class Wrapper {
    private String label;
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Test target;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Test getTarget() {
        return target;
    }

    public void setTarget(Test target) {
        this.target = target;
    }
}
