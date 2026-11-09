package io.micronaut.data.mongodb.init

import org.bson.Document
import spock.lang.Specification

class TextIndexDefinitionSpec extends Specification {

    void "an existing text index matches a definition with the same fields, weights and languages, whatever its name"() {
        given:
        def definition = new AbstractMongoCollectionsCreator.TextIndexDefinition("book_text", [title: 10, description: 2], "english", null)

        expect:
        definition.matches(index("other_name", [description: 2, title: 10], "english", "language"))
        // MongoDB leaves out the default language and language override
        definition.matches(new Document("name", "book_text").append("weights", new Document([title: 10, description: 2])))
    }

    void "an existing text index with other fields, weights or languages does not match"() {
        given:
        def definition = new AbstractMongoCollectionsCreator.TextIndexDefinition("book_text", [title: 10, description: 2], "english", "lang")

        expect:
        definition.matches(index("book_text", [title: 10, description: 2], "english", "lang"))
        !definition.matches(index("book_text", [title: 10], "english", "lang"))
        !definition.matches(index("book_text", [title: 5, description: 2], "english", "lang"))
        !definition.matches(index("book_text", [title: 10, description: 2], "german", "lang"))
        !definition.matches(index("book_text", [title: 10, description: 2], "english", "language"))
    }

    private static Document index(String name, Map<String, Integer> weights, String language, String languageOverride) {
        return new Document("name", name)
                .append("key", new Document("_fts", "text").append("_ftsx", 1))
                .append("weights", new Document(weights))
                .append("default_language", language)
                .append("language_override", languageOverride)
    }
}
