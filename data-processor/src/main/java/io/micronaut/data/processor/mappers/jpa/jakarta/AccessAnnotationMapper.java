package io.micronaut.data.processor.mappers.jpa.jakarta;

public class AccessAnnotationMapper extends io.micronaut.data.processor.mappers.jpa.jx.AccessAnnotationMapper {

    @Override
    public String getName() {
        return "jakarta.persistence.Access";
    }
}
