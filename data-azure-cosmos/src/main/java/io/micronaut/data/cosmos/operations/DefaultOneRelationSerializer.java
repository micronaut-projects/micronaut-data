package io.micronaut.data.cosmos.operations;

import io.micronaut.core.type.Argument;
import io.micronaut.data.document.serde.OneRelationSerializer;
import io.micronaut.serde.Encoder;
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
final class DefaultOneRelationSerializer implements OneRelationSerializer {
    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<?> type, Object value) throws IOException {
        encoder.encodeNull();
    }
}
