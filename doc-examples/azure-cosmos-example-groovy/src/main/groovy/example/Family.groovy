package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Transient
import io.micronaut.data.cosmos.annotation.ETag
import io.micronaut.data.cosmos.annotation.PartitionKey

@MappedEntity
class Family {

    @Id
    private String id
    @PartitionKey
    private String lastName
    @Relation(value = Relation.Kind.EMBEDDED)
    private Address address
    @Relation(value = Relation.Kind.ONE_TO_MANY)
    private List<Child> children = new ArrayList<>()
    private boolean registered
    private Date registeredDate
    private String[] tags
    // tag::locking[]
    @ETag
    private String documentVersion
    // end::locking[]
    @Transient
    private String comment

    String getId() {
        return id
    }

    void setId(String id) {
        this.id = id
    }

    String getLastName() {
        return lastName
    }

    void setLastName(String lastName) {
        this.lastName = lastName
    }

    Address getAddress() {
        return address
    }

    void setAddress(Address address) {
        this.address = address
    }

    List<Child> getChildren() {
        return children
    }

    void setChildren(List<Child> children) {
        this.children = children
    }

    boolean getRegistered() {
        return registered
    }

    void setRegistered(boolean registered) {
        this.registered = registered
    }

    Date getRegisteredDate() {
        return registeredDate
    }

    void setRegisteredDate(Date registeredDate) {
        this.registeredDate = registeredDate
    }

    String[] getTags() {
        return tags
    }

    void setTags(String[] tags) {
        this.tags = tags
    }

    String getDocumentVersion() {
        return documentVersion
    }

    void setDocumentVersion(String documentVersion) {
        this.documentVersion = documentVersion
    }

    String getComment() {
        return comment
    }

    void setComment(String comment) {
        this.comment = comment
    }
}
