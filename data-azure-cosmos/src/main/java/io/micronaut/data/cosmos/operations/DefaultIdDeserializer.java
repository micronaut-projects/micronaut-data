package io.micronaut.data.cosmos.operations;

import io.micronaut.core.type.Argument;
import io.micronaut.data.document.serde.IdDeserializer;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.CustomizableDeserializer;
import jakarta.inject.Singleton;

@Singleton
final class DefaultIdDeserializer implements IdDeserializer, CustomizableDeserializer<Object> {

    @Override
    public Deserializer<Object> createSpecific(DecoderContext context, Argument<? super Object> type) throws SerdeException {
        Deserializer<? extends Object> deserializer = context.findDeserializer(type);
        return (Deserializer<Object>) deserializer.createSpecific(context, type);
    }
}
