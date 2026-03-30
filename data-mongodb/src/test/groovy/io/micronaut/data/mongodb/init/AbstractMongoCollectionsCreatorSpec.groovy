package io.micronaut.data.mongodb.init

import com.mongodb.client.model.CollationStrength
import org.bson.Document
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class AbstractMongoCollectionsCreatorSpec extends Specification {

    void 'resolves clustered collection options from collection document'() {
        given:
        def collectionDocument = new Document('name', 'events')
                .append('options', new Document('clusteredIndex', new Document('name', 'clustered_idx').append('unique', true))
                        .append('expireAfterSeconds', 300))

        when:
        def options = AbstractMongoCollectionsCreator.toResolvedCollectionOptions(collectionDocument)

        then:
        options != null
        options.clusteredIndexName() == 'clustered_idx'
        options.clusteredIndexUnique()
        options.expireAfterSeconds() == 300
    }

    void 'skips id index when resolving index document'() {
        expect:
        AbstractMongoCollectionsCreator.toResolvedIndex(new Document('name', '_id_').append('key', new Document('_id', 1))) == null
    }

    void 'resolves text index using weights'() {
        given:
        def indexDocument = new Document('name', 'embedded_text_idx')
                .append('key', new Document('_fts', 'text').append('_ftsx', 1))
                .append('weights', new Document('details.city', 3))
                .append('default_language', 'english')
                .append('textIndexVersion', 3)

        when:
        def index = AbstractMongoCollectionsCreator.toResolvedIndex(indexDocument)

        then:
        index != null
        index.name() == 'embedded_text_idx'
        index.fields().size() == 1
        index.fields()[0].path() == 'details.city'
        index.fields()[0].weight() == 3
        index.fields()[0].kind() == 'text'
        index.defaultLanguage() == 'english'
        index.textIndexVersion() == 3
    }

    void 'builds create collection options from resolved options'() {
        when:
        def options = AbstractMongoCollectionsCreator.toCreateCollectionOptions(
                new AbstractMongoCollectionsCreator.MongoResolvedCollectionOptions('clustered_idx', true, 120)
        )

        then:
        options.clusteredIndexOptions != null
        options.clusteredIndexOptions.name == 'clustered_idx'
        options.getExpireAfter(TimeUnit.SECONDS) == 120L
    }

    void 'builds index options from resolved index'() {
        given:
        def index = new AbstractMongoCollectionsCreator.MongoResolvedIndex(
                'field_idx',
                [new AbstractMongoCollectionsCreator.MongoResolvedIndexField('location.state', 1, null, null, null, null)],
                true,
                true,
                true,
                60,
                '{ tenantId: 1 }',
                '{ locale: "en", strength: 2 }',
                26,
                -180d,
                180d,
                'english',
                'language',
                3,
                2,
                '{ location: 0 }',
                '{ wiredTiger: {} }',
                null,
                null
        )

        when:
        def options = AbstractMongoCollectionsCreator.toIndexOptions(index)

        then:
        options.name == 'field_idx'
        options.unique
        options.sparse
        options.hidden
        options.getExpireAfter(TimeUnit.SECONDS) == 60L
        options.bits == 26
        options.min == -180d
        options.max == 180d
        options.defaultLanguage == 'english'
        options.languageOverride == 'language'
        options.textVersion == 3
        options.sphereVersion == 2
    }

    void 'builds create indexes command document with comment and commit quorum'() {
        given:
        def index = new AbstractMongoCollectionsCreator.MongoResolvedIndex(
                'field_idx',
                [new AbstractMongoCollectionsCreator.MongoResolvedIndexField('location.state', 1, null, null, null, null)],
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                'create embedded index',
                'majority'
        )

        when:
        def command = AbstractMongoCollectionsCreator.toCreateIndexesCommandDocument('entities', index)

        then:
        command.getString('createIndexes') == 'entities'
        command.getString('comment') == 'create embedded index'
        command.get('commitQuorum') == 'majority'
        command.getList('indexes', Object).size() == 1
    }

    void 'converts numeric and symbolic commit quorum values'() {
        expect:
        AbstractMongoCollectionsCreator.toCommitQuorumValue('2') == 2
        AbstractMongoCollectionsCreator.toCommitQuorumValue('majority') == 'majority'
    }

    void 'converts document to collation'() {
        when:
        def collation = AbstractMongoCollectionsCreator.toCollation(new Document('locale', 'en').append('strength', 2).append('caseLevel', true))

        then:
        collation.locale == 'en'
        collation.strength == CollationStrength.SECONDARY
        collation.caseLevel
    }
}
