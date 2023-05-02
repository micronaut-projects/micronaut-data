package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity(value = "TBL_CONTACT", alias = "c")
public class Contact {
    @Id
    private Long id;
    private String name;
    private int age;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
}
