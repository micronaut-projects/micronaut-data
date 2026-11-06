package io.micronaut.data.search.elasticsearch;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;
import org.jspecify.annotations.Nullable;

/**
 * An entity with optimistic locking.
 */
@MappedEntity("versioned_book")
@Searchable
public class VersionedBook {

    @Id
    private String id;

    @Version
    @Nullable
    private Long version;

    @SearchField
    private String title;

    public VersionedBook(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Nullable
    public Long getVersion() {
        return version;
    }

    public void setVersion(@Nullable Long version) {
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
