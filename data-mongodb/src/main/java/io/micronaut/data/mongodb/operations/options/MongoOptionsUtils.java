/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.mongodb.operations.options;

import com.mongodb.CursorType;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationAlternate;
import com.mongodb.client.model.CollationCaseFirst;
import com.mongodb.client.model.CollationMaxVariable;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.InsertOneOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.mongodb.annotation.MongoCollation;
import io.micronaut.data.mongodb.annotation.MongoDeleteOptions;
import io.micronaut.data.mongodb.annotation.MongoUpdateOptions;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import java.util.Optional;

/**
 * Mongo internal utils for building options.
 */
@Internal
public final class MongoOptionsUtils {

    private MongoOptionsUtils() {
    }

    public static Optional<UpdateOptions> buildUpdateOptions(AnnotationMetadata annotationMetadata, boolean includeCollation) {
        AnnotationValue<MongoUpdateOptions> optionsAnn = annotationMetadata.getAnnotation(MongoUpdateOptions.class);
        if (optionsAnn == null) {
            return Optional.empty();
        }
        UpdateOptions options = new UpdateOptions();
        optionsAnn.booleanValue("upsert").ifPresent(options::upsert);
        optionsAnn.booleanValue("bypassDocumentValidation").ifPresent(options::bypassDocumentValidation);
        optionsAnn.stringValue("hint").map(BsonDocument::parse).ifPresent(options::hint);
        if (includeCollation) {
            annotationMetadata.stringValue(MongoCollation.class)
                    .map(BsonDocument::parse)
                    .ifPresent(bsonDocument -> options.collation(bsonDocumentAsCollation(bsonDocument)));

        }
        return Optional.of(options);
    }

    public static Optional<ReplaceOptions> buildReplaceOptions(AnnotationMetadata annotationMetadata) {
        AnnotationValue<MongoUpdateOptions> optionsAnn = annotationMetadata.getAnnotation(MongoUpdateOptions.class);
        if (optionsAnn == null) {
            return Optional.empty();
        }
        ReplaceOptions options = new ReplaceOptions();
        optionsAnn.booleanValue("upsert").ifPresent(options::upsert);
        optionsAnn.booleanValue("bypassDocumentValidation").ifPresent(options::bypassDocumentValidation);
        optionsAnn.stringValue("hint").map(BsonDocument::parse).ifPresent(options::hint);
        annotationMetadata.stringValue(MongoCollation.class)
                .map(BsonDocument::parse)
                .ifPresent(bsonDocument -> options.collation(bsonDocumentAsCollation(bsonDocument)));
        return Optional.of(options);
    }

    public static Optional<InsertOneOptions> buildInsertOneOptions(AnnotationMetadata annotationMetadata) {
        // Future annotation support
        return Optional.empty();
    }

    public static Optional<InsertManyOptions> buildInsertManyOptions(AnnotationMetadata annotationMetadata) {
        // Future annotation support
        return Optional.empty();
    }

    public static Optional<MongoFindOptions> buildFindOptions(AnnotationMetadata annotationMetadata) {
        AnnotationValue<io.micronaut.data.mongodb.annotation.MongoFindOptions> optionsAnn = annotationMetadata
                .getAnnotation(io.micronaut.data.mongodb.annotation.MongoFindOptions.class);
        if (optionsAnn == null) {
            return Optional.empty();
        }
        MongoFindOptions options = new MongoFindOptions();
        optionsAnn.intValue("batchSize").ifPresent(options::batchSize);
        optionsAnn.intValue("skip").ifPresent(options::skip);
        optionsAnn.intValue("limit").ifPresent(options::limit);
        optionsAnn.longValue("maxTimeMS").ifPresent(options::maxTimeMS);
        optionsAnn.longValue("maxAwaitTimeMS").ifPresent(options::maxAwaitTimeMS);
        optionsAnn.enumValue("cursorType", CursorType.class).ifPresent(options::cursorType);
        optionsAnn.booleanValue("noCursorTimeout").ifPresent(options::noCursorTimeout);
        optionsAnn.booleanValue("partial").ifPresent(options::partial);
        optionsAnn.stringValue("comment").ifPresent(options::comment);
        optionsAnn.stringValue("hint").map(BsonDocument::parse).ifPresent(options::hint);
        optionsAnn.stringValue("max").map(BsonDocument::parse).ifPresent(options::max);
        optionsAnn.stringValue("min").map(BsonDocument::parse).ifPresent(options::min);
        optionsAnn.booleanValue("returnKey").ifPresent(options::returnKey);
        optionsAnn.booleanValue("showRecordId").ifPresent(options::showRecordId);
        optionsAnn.booleanValue("allowDiskUse").ifPresent(options::allowDiskUse);
        if (options.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(options);
    }

    public static Optional<MongoAggregationOptions> buildAggregateOptions(AnnotationMetadata annotationMetadata) {
        AnnotationValue<io.micronaut.data.mongodb.annotation.MongoAggregateOptions> optionsAnn = annotationMetadata
                .getAnnotation(io.micronaut.data.mongodb.annotation.MongoAggregateOptions.class);
        if (optionsAnn == null) {
            return Optional.empty();
        }
        MongoAggregationOptions options = new MongoAggregationOptions();
        optionsAnn.booleanValue("bypassDocumentValidation").ifPresent(options::bypassDocumentValidation);
        optionsAnn.longValue("maxTimeMS").ifPresent(options::maxTimeMS);
        optionsAnn.longValue("maxAwaitTimeMS").ifPresent(options::maxAwaitTimeMS);
        optionsAnn.stringValue("comment").ifPresent(options::comment);
        optionsAnn.stringValue("hint").map(BsonDocument::parse).ifPresent(options::hint);
        optionsAnn.booleanValue("allowDiskUse").ifPresent(options::allowDiskUse);
        if (options.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(options);
    }

    public static Optional<DeleteOptions> buildDeleteOptions(AnnotationMetadata annotationMetadata, boolean includeCollation) {
        AnnotationValue<MongoDeleteOptions> optionsAnn = annotationMetadata
                .getAnnotation(MongoDeleteOptions.class);
        if (optionsAnn == null) {
            return Optional.empty();
        }
        DeleteOptions options = new DeleteOptions();
        optionsAnn.stringValue("hint").map(BsonDocument::parse).ifPresent(options::hint);
        annotationMetadata.stringValue(MongoCollation.class)
                .map(BsonDocument::parse)
                .ifPresent(bsonDocument -> options.collation(bsonDocumentAsCollation(bsonDocument)));
        return Optional.of(options);
    }

    public static Collation bsonDocumentAsCollation(@Nullable BsonDocument collationDocument) {
        if (collationDocument == null) {
            return null;
        }
        Collation.Builder builder = Collation.builder();
        BsonValue locale = collationDocument.get("locale");
        if (locale != null) {
            builder.locale(locale.asString().getValue());
        }
        BsonValue caseLevel = collationDocument.get("caseLevel");
        if (caseLevel != null) {
            builder.caseLevel(caseLevel.asBoolean().getValue());
        }
        BsonValue caseFirst = collationDocument.get("caseFirst");
        if (caseFirst != null) {
            builder.collationCaseFirst(CollationCaseFirst.valueOf(caseFirst.asString().getValue()));
        }
        BsonValue strength = collationDocument.get("strength");
        if (strength != null) {
            builder.collationStrength(CollationStrength.valueOf(strength.asString().getValue()));
        }
        BsonValue numericOrdering = collationDocument.get("numericOrdering");
        if (numericOrdering != null) {
            builder.numericOrdering(numericOrdering.asBoolean().getValue());
        }
        BsonValue alternate = collationDocument.get("alternate");
        if (alternate != null) {
            builder.collationAlternate(CollationAlternate.valueOf(alternate.asString().getValue()));
        }
        BsonValue maxVariable = collationDocument.get("maxVariable");
        if (maxVariable != null) {
            builder.collationMaxVariable(CollationMaxVariable.valueOf(maxVariable.asString().getValue()));
        }
        BsonValue normalization = collationDocument.get("normalization");
        if (normalization != null) {
            builder.normalization(normalization.asBoolean().getValue());
        }
        BsonValue backwards = collationDocument.get("backwards");
        if (backwards != null) {
            builder.backwards(backwards.asBoolean().getValue());
        }
        return builder.build();
    }

}
