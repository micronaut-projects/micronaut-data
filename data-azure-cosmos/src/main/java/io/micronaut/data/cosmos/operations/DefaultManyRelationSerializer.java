package io.micronaut.data.cosmos.operations;

import io.micronaut.core.type.Argument;
import io.micronaut.data.document.serde.ManyRelationSerializer;
import io.micronaut.serde.Encoder;
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
final class DefaultManyRelationSerializer implements ManyRelationSerializer {
    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<?> type, Object value) throws IOException {
        encoder.encodeNull();
    }
}
