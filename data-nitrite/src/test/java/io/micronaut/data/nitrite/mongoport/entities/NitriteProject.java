package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.util.Objects;

@MappedEntity("nitrite_project")
public class NitriteProject {
    @Id
    private NitriteProjectId id;
    private String name;

    public NitriteProject() {
    }

    public NitriteProjectId getId() {
        return id;
    }

    public void setId(NitriteProjectId id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NitriteProject that = (NitriteProject) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}
