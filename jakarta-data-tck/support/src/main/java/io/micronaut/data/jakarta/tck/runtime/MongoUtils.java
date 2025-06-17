package io.micronaut.data.jakarta.tck.runtime;

import io.micronaut.inject.ast.FieldElement;
import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

class MongoUtils {
    public static void bson(FieldElement element) {
        element.annotate(BsonRepresentation.class, builder -> builder.value(BsonType.STRING));
    }
}
