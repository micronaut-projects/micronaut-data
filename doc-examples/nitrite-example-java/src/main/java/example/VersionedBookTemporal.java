package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;

import java.time.Instant;

// tag::versioned-book-temporal[]
@MappedEntity
public class VersionedBookTemporal {
    @Id
    @GeneratedValue
    private String id;

    private String title;

    @Version
    private Instant version; // <1>

    public VersionedBookTemporal() {
    }

    public VersionedBookTemporal(String title) {
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

    public Instant getVersion() {
        return version;
    }

    public void setVersion(Instant version) {
        this.version = version;
    }
}
// end::versioned-book-temporal[]
