package io.micronaut.data.document.mongodb

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import io.micronaut.data.mongodb.init.AbstractMongoCollectionsCreator
import org.bson.Document

final class MongoIndexInspector {

    private MongoIndexInspector() {
    }

    static List<Map<String, Object>> listNormalizedIndexes(MongoClient mongoClient, String databaseName, String collectionName) {
        MongoCollection<Document> mongoCollection = mongoClient.getDatabase(databaseName).getCollection(collectionName)
        List<Map<String, Object>> indexes = []
        for (Document indexDocument : mongoCollection.listIndexes()) {
            Document keyDocument = indexDocument.get('key', Document)
            if (keyDocument == null || (keyDocument.size() == 1 && keyDocument.getInteger('_id', 0) == 1)) {
                continue
            }
            List<AbstractMongoCollectionsCreator.MongoResolvedIndexField> fields = new ArrayList<>(keyDocument.size())
            for (Map.Entry<String, Object> entry : keyDocument.entrySet()) {
                Object value = entry.getValue()
                if (value instanceof Number) {
                    fields.add(new AbstractMongoCollectionsCreator.MongoResolvedIndexField(entry.getKey(), ((Number) value).intValue(), null, null, null, null))
                } else {
                    fields.add(new AbstractMongoCollectionsCreator.MongoResolvedIndexField(entry.getKey(), null, null, value.toString(), null, null))
                }
            }
            indexes << [
                    name              : indexDocument.getString('name'),
                    fields            : List.copyOf(fields),
                    unique            : indexDocument.getBoolean('unique', false),
                    sparse            : indexDocument.getBoolean('sparse', false),
                    hidden            : indexDocument.getBoolean('hidden', false),
                    expireAfterSeconds: indexDocument.getInteger('expireAfterSeconds'),
                    partialFilterExpression: indexDocument.get('partialFilterExpression'),
                    collation            : indexDocument.get('collation'),
                    wildcardProjection   : indexDocument.get('wildcardProjection'),
                    min                  : indexDocument.get('min'),
                    max                  : indexDocument.get('max'),
                    defaultLanguage      : indexDocument.getString('default_language'),
                    languageOverride     : indexDocument.getString('language_override'),
                    textIndexVersion     : indexDocument.getInteger('textIndexVersion'),
                    sphereVersion        : indexDocument.getInteger('2dsphereIndexVersion'),
            ]
        }
        indexes
    }
}
