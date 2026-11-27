package io.micronaut.data.graph.tck.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.graph.annotation.Node;

@Node
public class Article {
    @Id
    private final String slug;
    private String title;
    @Version
    private Long version;

    public Article(String slug, String title) {
        this.slug = slug;
        this.title = title;
    }

    public String getSlug() {
        return slug;
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
