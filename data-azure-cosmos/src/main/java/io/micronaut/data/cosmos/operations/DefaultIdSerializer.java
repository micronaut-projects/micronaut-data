package io.micronaut.data.cosmos.operations;

import io.micronaut.core.type.Argument;
import io.micronaut.data.document.serde.IdSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.CustomizableSerializer;
import jakarta.inject.Singleton;

@Singleton
final class DefaultIdSerializer implements IdSerializer, CustomizableSerializer<Object> {

    @Override
    public Serializer<Object> createSpecific(EncoderContext context, Argument<?> type) throws SerdeException {
        Serializer<? super Object> serializer = context.findSerializer(type);
        return serializer.createSpecific(context, type);
    }
}
