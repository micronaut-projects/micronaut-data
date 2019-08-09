package io.micronaut.data.processor.visitors;

import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.inject.ast.ClassElement;

import java.util.function.Function;

class TestUtils {

    static SourcePersistentEntity sourcePersistentEntity(ClassElement classElement) {
        return new SourcePersistentEntity(classElement, new Function<ClassElement, SourcePersistentEntity>() {
            @Override
            public SourcePersistentEntity apply(ClassElement classElement) {
                return new SourcePersistentEntity(classElement, this);
            }
        });
    }
}
