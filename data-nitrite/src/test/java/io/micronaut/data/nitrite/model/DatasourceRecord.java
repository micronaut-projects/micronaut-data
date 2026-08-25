package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Entity written through more than one datasource, so that a test can show each datasource keeps
 * its own documents.
 */
@MappedEntity
public class DatasourceRecord {

    @Id
    @GeneratedValue
    private String id;

    private String label;

    public DatasourceRecord() {
    }

    public DatasourceRecord(String label) {
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
