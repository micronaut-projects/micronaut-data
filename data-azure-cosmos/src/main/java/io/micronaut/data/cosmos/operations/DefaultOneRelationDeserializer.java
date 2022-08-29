package io.micronaut.data.cosmos.operations;

import io.micronaut.core.type.Argument;
import io.micronaut.data.document.serde.OneRelationDeserializer;
import io.micronaut.serde.Decoder;
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
final class DefaultOneRelationDeserializer implements OneRelationDeserializer {

    @Override
    public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        return null;
    }
}
