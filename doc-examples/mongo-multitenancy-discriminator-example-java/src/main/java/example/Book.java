
package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TenantId;
import org.bson.types.ObjectId;

@MappedEntity
public class Book {
    @Id
    @GeneratedValue
    private ObjectId id;
    private String title;
    private int pages;
    @TenantId
    private String tenancyId;

    public Book(String title, int pages) {
        this.title = title;
        this.pages = pages;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public int getPages() {
        return pages;
    }

    public String getTenancyId() {
        return tenancyId;
    }

    public void setTenancyId(String tenancyId) {
        this.tenancyId = tenancyId;
    }
}
