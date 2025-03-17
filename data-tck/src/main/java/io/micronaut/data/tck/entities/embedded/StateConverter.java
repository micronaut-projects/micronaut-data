package io.micronaut.data.tck.entities.embedded;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
class StateConverter implements AttributeConverter<Enum, String> {

    @Override
    public String convertToDatabaseColumn(Enum anEnum) {
        if (anEnum == null) {
            return null;
        }
        return anEnum.name();
    }

    @Override
    public Enum convertToEntityAttribute(String string) {
        if (string == null) {
            return null;
        }
        // Because enum generics in ResourceEntity then implement this
        // simple converter just to be able to run tests
        if (string.equals("BORROWED")) {
            return BookState.BORROWED;
        } else if (string.equals("READ")) {
            return BookState.READ;
        } else if (string.equals("RETURNED")) {
            return BookState.RETURNED;
        } else if (string.equals("BUILDING")) {
            return HouseState.BUILDING;
        } else if (string.equals("FINISHED")) {
            return HouseState.FINISHED;
        }
        throw new IllegalStateException("Unexpected enum value: " + string);
    }
}
