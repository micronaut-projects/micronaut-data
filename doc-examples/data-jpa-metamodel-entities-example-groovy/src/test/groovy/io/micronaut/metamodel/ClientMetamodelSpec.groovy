package io.micronaut.metamodel

import io.micronaut.data.tck.entities.Client
import io.micronaut.data.tck.entities.ClientCategory
import io.micronaut.data.tck.entities.Client_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.CollectionAttribute
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.ListAttribute
import jakarta.persistence.metamodel.SetAttribute
import jakarta.persistence.metamodel.SingularAttribute

import java.time.Instant

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.*

class ClientMetamodelSpec extends AbstractEntityMetamodelSpec {

    final def CLIENT_CLASS_NAME = Client.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                Client,
                Client_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [Long], CLIENT_CLASS_NAME),
                        new Attribute("name", SingularAttribute, [String], CLIENT_CLASS_NAME),
                        new Attribute("version", SingularAttribute, [Long], CLIENT_CLASS_NAME),
                        new Attribute("tier", SingularAttribute, [Client.Tier], CLIENT_CLASS_NAME),
                        new Attribute("createdAt", SingularAttribute, [Instant], CLIENT_CLASS_NAME),

                        new Attribute("billingAddress", SingularAttribute, [Client.Address], CLIENT_CLASS_NAME),

                        new Attribute("categoriesCollection", CollectionAttribute, [ClientCategory], CLIENT_CLASS_NAME),
                        new Attribute("categoriesList", ListAttribute, [ClientCategory], CLIENT_CLASS_NAME),
                        new Attribute("categoriesSet", SetAttribute, [ClientCategory], CLIENT_CLASS_NAME),

                        new Attribute("mainCategory", SingularAttribute, [ClientCategory], CLIENT_CLASS_NAME),
                ),
                List.of("nonPersistent")
        )
    }
}
