/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.model;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;

import java.io.IOException;
import java.util.List;

/**
 * Custom serializer for {@link Page} as a workaround for https://github.com/micronaut-projects/micronaut-serialization/issues/307.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Prototype
@Internal
class PageSerializer implements Serializer<Page<Object>> {

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Page<Object>> type, Page<Object> page) throws IOException {
        serializePage(encoder, context, type, page);
    }

    /**
     * Serializes {@link Page}.
     *
     * @param encoder the encoder
     * @param context the encoder context
     * @param type the Page type
     * @param page the Page object
     * @throws IOException if an error occurs
     */
    public static void serializePage(Encoder encoder, EncoderContext context, Argument<? extends Page<Object>> type, Page<Object> page) throws IOException {
        Encoder e = encoder.encodeObject(type);

        e.encodeKey("content");
        Argument<List<Object>> contentType = Argument.listOf((Argument<Object>) type.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT));
        context.findSerializer(contentType)
            .createSpecific(context, contentType)
            .serialize(e, context, contentType, page.getContent());

        e.encodeKey("pageable");
        Argument<Pageable> pageable = Argument.of(Pageable.class);
        context.findSerializer(pageable)
            .createSpecific(context, pageable)
            .serialize(e, context, pageable, page.getPageable());

        e.encodeKey("totalSize");
        e.encodeLong(page.getTotalSize());

        e.finishStructure();
    }
}
