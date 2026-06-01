package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class NitriteDocumentOwner {

    private String name;
    private int age;

    public NitriteDocumentOwner() {}

    public NitriteDocumentOwner(String name) { this.name = name; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
}
