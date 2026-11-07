package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.graph.annotation.Node;

/**
 * A versioned node with an assigned identifier and no repository of its own: it is written through a shelf.
 */
@Node
public class Volume {
    @Id
    private final String code;
    @Index(columns = "isbn", unique = true)
    private final String isbn;
    @Version
    private Long version;

    public Volume(String code, String isbn) {
        this.code = code;
        this.isbn = isbn;
    }

    public String getCode() {
        return code;
    }

    public String getIsbn() {
        return isbn;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
