package io.micronaut.data.jdbc.h2.snake;

import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Relation;

@MappedEntity
public class CBook {
    @Id
    @GeneratedValue
    private Long id;
    private String title;
    private int total_pages;
    @Relation(Relation.Kind.MANY_TO_ONE)
    private CAuthor author;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getTotal_pages() { return total_pages; }
    public void setTotal_pages(int total_pages) { this.total_pages = total_pages; }
    public CAuthor getAuthor() { return author; }
    public void setAuthor(CAuthor author) { this.author = author; }
}
