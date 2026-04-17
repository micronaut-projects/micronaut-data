package io.micronaut;

import java.util.List;


public record ExpectedMetamodel(
    Class<?> entityClass,
    Class<?> metamodelClass,
    Class<?> jakartaManagedType,
    List<Attribute> attributes,
    List<String> forbiddenFields
) {
    public record Attribute(
        String constantName,
        Class<?> attributeType,
        List<Class<?>> fieldTypes,
        String declaringType
    ) {
    }

}
