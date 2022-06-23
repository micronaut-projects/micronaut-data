package io.micronaut.data.hibernate.reactive;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter(autoApply = true)
public class CustomConverter implements AttributeConverter<A, B> {

    @Override
    public B convertToDatabaseColumn(A attribute) {
        return null;
    }

    @Override
    public A convertToEntityAttribute(B dbData) {
        return null;
    }

}

class A {}
class B {}
