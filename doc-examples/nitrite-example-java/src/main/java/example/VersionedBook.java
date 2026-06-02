package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;

// tag::versioned-book[]
@MappedEntity
public class VersionedBook {
    @Id
    @GeneratedValue
    private String id;

    private String title;

    @Version
    private Long version; // <1>

    public VersionedBook() {
    }

    public VersionedBook(String title) {
        this.title = title;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
// end::versioned-book[]
