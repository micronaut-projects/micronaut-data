package io.micronaut.data.runtime.criteria;

import io.micronaut.data.annotation.Relation;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Holder {
    @Id
    private Long id;
    // Same name as the association of the wrapper, a join must not resolve to it
    @ManyToOne
    private SimpleEntity target;
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Wrapper wrapper;
    @ManyToOne
    private Holder parent;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SimpleEntity getTarget() {
        return target;
    }

    public void setTarget(SimpleEntity target) {
        this.target = target;
    }

    public Wrapper getWrapper() {
        return wrapper;
    }

    public void setWrapper(Wrapper wrapper) {
        this.wrapper = wrapper;
    }

    public Holder getParent() {
        return parent;
    }

    public void setParent(Holder parent) {
        this.parent = parent;
    }
}
