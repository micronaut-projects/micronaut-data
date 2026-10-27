package io.micronaut.data.search.elasticsearch;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;

/**
 * An entity whose index name is overridden.
 */
@MappedEntity
@Searchable(name = "named_books")
public class NamedBook {

    @Id
    private String id;

    @SearchField
    private String title;

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
}
