package io.micronaut.metamodel

import io.micronaut.data.tck.entities.*
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.ListAttribute
import jakarta.persistence.metamodel.SetAttribute
import jakarta.persistence.metamodel.SingularAttribute

import java.time.LocalDateTime

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute

class BookMetamodelSpec extends AbstractEntityMetamodelSpec {

    final def BOOK_CLASS_NAME = Book.getName()

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                Book,
                Book_,
                EntityType,
                List.of(new Attribute("id", SingularAttribute, [Long], BOOK_CLASS_NAME),
                        new Attribute("title", SingularAttribute, [String], BOOK_CLASS_NAME),
                        new Attribute("totalPages", SingularAttribute, [Integer], BOOK_CLASS_NAME),
                        new Attribute("author", SingularAttribute, [Author], BOOK_CLASS_NAME),
                        new Attribute("genre", SingularAttribute, [Genre], BOOK_CLASS_NAME),
                        new Attribute("publisher", SingularAttribute, [Publisher], BOOK_CLASS_NAME),
                        new Attribute("pages", ListAttribute, [Page], BOOK_CLASS_NAME),
                        new Attribute("chapters", ListAttribute, [Chapter], BOOK_CLASS_NAME),
                        new Attribute("students", SetAttribute, [Student], Book.class.getName()),
                        new Attribute("lastUpdated", SingularAttribute, [LocalDateTime], BOOK_CLASS_NAME)),
                List.of("prePersist", "postPersist", "preUpdate", "postUpdate", "preRemove", "postRemove", "postLoad"));
    }
}


