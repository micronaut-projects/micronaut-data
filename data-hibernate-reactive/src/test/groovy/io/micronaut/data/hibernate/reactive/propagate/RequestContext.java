package io.micronaut.data.hibernate.reactive.propagate;

import io.micronaut.runtime.http.scope.RequestScope;

@RequestScope
public class RequestContext {

    private Long id;

    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
