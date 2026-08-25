package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.nitrite.annotation.FullTextIndex;

/**
 * Entity with a unique index and a full-text index, used to regression-test concurrent inserts
 * against those index types (nitrite 4.4.1 fixed a ConcurrentModificationException / false
 * unique-constraint violation race under concurrent writes to unique/full-text indexes).
 */
@MappedEntity
public class UniqueIndexedEntity {
    @Id
    @GeneratedValue
    private Long id;

    @Index(columns = "code", unique = true)
    private String code;

    @FullTextIndex
    private String description;

    public UniqueIndexedEntity() {
    }

    public UniqueIndexedEntity(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
