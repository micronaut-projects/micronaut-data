package io.micronaut.data.hibernate.reactive.entities;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class AuthorDto {

    private final Long authorId;
    private final String authorName;

    public AuthorDto(Long authorId, String authorName) {
        this.authorId = authorId;
        this.authorName = authorName;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }
}
