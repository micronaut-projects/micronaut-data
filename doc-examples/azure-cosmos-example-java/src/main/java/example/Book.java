
package example;

import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.cosmos.annotation.PartitionKey;

import java.util.Date;

// tag::book[]
@MappedEntity
public class Book {
    @Id
    @GeneratedValue
    @PartitionKey
    private String id;
    private String title;
    private int pages;
    @DateCreated
    private Date createdDate;
    @DateUpdated
    private Date updatedDate;

    public Book(String title, int pages) {
        this.title = title;
        this.pages = pages;
    }
    // end::book[]

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public int getPages() {
        return pages;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(Date updatedDate) {
        this.updatedDate = updatedDate;
    }
    // tag::book[]
    // ...
}
// end::book[]
