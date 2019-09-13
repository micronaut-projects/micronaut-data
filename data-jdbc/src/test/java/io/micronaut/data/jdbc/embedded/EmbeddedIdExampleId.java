package io.micronaut.data.jdbc.embedded;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.DataType;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class EmbeddedIdExampleId implements Serializable {

    @MappedProperty(value = "p", type = DataType.STRING)
    private final String p;

    @MappedProperty(value = "t", type = DataType.STRING)
    private final String t;

    public EmbeddedIdExampleId(String p, String t) {
        this.p = p;
        this.t = t;
    }

    public String getP() {
        return p;
    }

    public String getT() {
        return t;
    }

    @Override
    public String toString() {
        return "TableId{" +
                "p='" + p + '\'' +
                ", t='" + t + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddedIdExampleId tableId = (EmbeddedIdExampleId) o;
        return Objects.equals(p, tableId.p) &&
                Objects.equals(t, tableId.t);
    }

    @Override
    public int hashCode() {
        return Objects.hash(p, t);
    }
}
