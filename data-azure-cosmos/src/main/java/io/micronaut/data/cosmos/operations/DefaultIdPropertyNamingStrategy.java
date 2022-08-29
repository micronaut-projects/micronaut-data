package io.micronaut.data.cosmos.operations;

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.data.document.serde.IdPropertyNamingStrategy;
import jakarta.inject.Singleton;

@Singleton
final class DefaultIdPropertyNamingStrategy implements IdPropertyNamingStrategy {
    @Override
    public String translate(AnnotatedElement element) {
        return "id";
    }
}
