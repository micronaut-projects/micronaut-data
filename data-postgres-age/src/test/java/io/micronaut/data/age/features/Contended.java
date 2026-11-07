package io.micronaut.data.age.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.graph.annotation.Node;

/**
 * A node no repository reaches, so that only the specs that create its label and indexes do so.
 */
@Node
public class Contended {
    @Id
    private final String code;
    @Index(columns = "text")
    private final String text;

    public Contended(String code, String text) {
        this.code = code;
        this.text = text;
    }

    public String getCode() {
        return code;
    }

    public String getText() {
        return text;
    }
}
