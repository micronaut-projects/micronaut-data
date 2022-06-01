package io.micronaut.data.r2dbc.postgres.http;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.io.Serializable;

/**
 * Represents sample.
 * 
 * @author seprokof
 *
 */
@Introspected
@MappedEntity(value = "pg_sample", alias = "sample")
public class SampleEntity implements Serializable {
    private static final long serialVersionUID = -5410891143601576439L;

    @Id
    @GeneratedValue
    private Long id;
    private String data;

    public SampleEntity() {
    }

    public SampleEntity(Long id, String data) {
        this.id = id;
        this.data = data;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
