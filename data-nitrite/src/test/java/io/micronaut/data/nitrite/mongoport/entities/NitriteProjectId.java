package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.data.annotation.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class NitriteProjectId implements Serializable {
    private String code;
    private String country;

    public NitriteProjectId() {
    }

    public NitriteProjectId(String code, String country) {
        this.code = code;
        this.country = country;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NitriteProjectId that = (NitriteProjectId) o;
        return Objects.equals(code, that.code) && Objects.equals(country, that.country);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, country);
    }
}
