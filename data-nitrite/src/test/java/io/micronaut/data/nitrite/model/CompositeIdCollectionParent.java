package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.ArrayList;
import java.util.List;

@MappedEntity
public class CompositeIdCollectionParent {

    @Id
    private String tenantId;

    @Id
    private String refId;

    private String name;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent")
    private List<CompositeIdCollectionChild> children = new ArrayList<>();

    public CompositeIdCollectionParent() {
    }

    public CompositeIdCollectionParent(String tenantId, String refId, String name) {
        this.tenantId = tenantId;
        this.refId = refId;
        this.name = name;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getRefId() {
        return refId;
    }

    public void setRefId(String refId) {
        this.refId = refId;
    }

    public String getName() {
        return name;
    }

    public List<CompositeIdCollectionChild> getChildren() {
        return children;
    }

    public void setChildren(List<CompositeIdCollectionChild> children) {
        this.children = children;
    }
}
