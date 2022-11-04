
package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.cosmos.annotation.PartitionKey;

// tag::book[]
@MappedEntity
public class Book {
    @Id
    @GeneratedValue
    @PartitionKey
    private String id;
    private String title;
    private int pages;

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
    // tag::book[]
    // ...
}
// end::book[]
