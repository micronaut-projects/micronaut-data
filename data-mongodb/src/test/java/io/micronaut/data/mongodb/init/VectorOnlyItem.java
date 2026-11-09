package io.micronaut.data.mongodb.init;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.mongodb.annotation.MongoSearchIndex;
import jakarta.persistence.Column;
import org.jspecify.annotations.Nullable;

/**
 * An entity searched only by vector, without search fields.
 */
@MappedEntity("vector_only_item")
@MongoSearchIndex(similarity = MongoSearchIndex.Similarity.DOT_PRODUCT)
public class VectorOnlyItem {

    @Id
    @GeneratedValue
    @Nullable
    private String id;
    @Column(length = 4)
    @Nullable
    private FloatVector embedding;

    @Nullable
    public String getId() {
        return id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }

    @Nullable
    public FloatVector getEmbedding() {
        return embedding;
    }

    public void setEmbedding(@Nullable FloatVector embedding) {
        this.embedding = embedding;
    }
}
