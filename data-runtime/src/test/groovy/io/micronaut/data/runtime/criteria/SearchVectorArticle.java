package io.micronaut.data.runtime.criteria;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.vector.FloatVector;
import jakarta.persistence.Column;

@MappedEntity("vector_article")
@Searchable
public class SearchVectorArticle {

    @Id
    @GeneratedValue
    private Long id;
    @SearchField
    private String title;
    @Column(length = 3)
    private FloatVector embedding;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public FloatVector getEmbedding() {
        return embedding;
    }

    public void setEmbedding(FloatVector embedding) {
        this.embedding = embedding;
    }
}
