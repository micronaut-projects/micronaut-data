package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.cosmos.annotation.ETag;
import io.micronaut.data.cosmos.annotation.PartitionKey;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@MappedEntity
public class Family {

    @Id
    private String id;

    @PartitionKey
    private String lastName;

    @Relation(value = Relation.Kind.EMBEDDED)
    private Address address;

    @Relation(value = Relation.Kind.ONE_TO_MANY)
    private List<Child> children = new ArrayList<>();

    private boolean registered;

    private Date registeredDate;

    private String[] tags;

    // tag::locking[]
    @ETag
    private String documentVersion;
    // end::locking[]

    @Transient
    private String comment;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public List<Child> getChildren() {
        return children;
    }

    public void setChildren(List<Child> children) {
        this.children = children;
    }

    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    public Date getRegisteredDate() {
        return registeredDate;
    }

    public void setRegisteredDate(Date registeredDate) {
        this.registeredDate = registeredDate;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public String getDocumentVersion() {
        return documentVersion;
    }

    public void setDocumentVersion(String documentVersion) {
        this.documentVersion = documentVersion;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
