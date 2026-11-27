package io.micronaut.data.document.mongodb.search;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchWeight;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.mongodb.annotation.MongoSearchIndex;
import jakarta.persistence.Column;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The TCK {@link io.micronaut.data.search.tck.entities.Article} searched with MongoDB Search and {@code $vectorSearch}.
 */
@MappedEntity("atlas_article")
@Searchable(language = "english")
@MongoSearchIndex
public class AtlasArticle {

    @Id
    @AutoPopulated
    @Nullable
    private UUID id;
    @SearchField(weight = SearchWeight.HIGH, highlightable = true)
    private String title;
    @SearchField
    private String body;
    @Column(length = 3)
    private FloatVector embedding;

    public AtlasArticle(String title, String body, FloatVector embedding) {
        this.title = title;
        this.body = body;
        this.embedding = embedding;
    }

    @Nullable
    public UUID getId() {
        return id;
    }

    public void setId(@Nullable UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public FloatVector getEmbedding() {
        return embedding;
    }

    public void setEmbedding(FloatVector embedding) {
        this.embedding = embedding;
    }
}
