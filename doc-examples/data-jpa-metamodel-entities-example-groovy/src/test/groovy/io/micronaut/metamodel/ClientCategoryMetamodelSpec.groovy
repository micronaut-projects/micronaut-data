package io.micronaut.metamodel

import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.ClientCategory
import io.micronaut.data.tck.entities.ClientCategory_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.ListAttribute
import jakarta.persistence.metamodel.SingularAttribute

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute;

class ClientCategoryMetamodelSpec extends AbstractEntityMetamodelSpec {

    final def CLIENT_CATEGORY_CLASS_NAME = ClientCategory.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                ClientCategory,
                ClientCategory_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [Long], CLIENT_CATEGORY_CLASS_NAME),
                        new Attribute("name", SingularAttribute, [String], CLIENT_CATEGORY_CLASS_NAME),
                        new Attribute("books", ListAttribute, [Book], CLIENT_CATEGORY_CLASS_NAME),
                        new Attribute("bytes", SingularAttribute, [byte[].class], CLIENT_CATEGORY_CLASS_NAME),
                ),
                List.of()
        )
    }
}
