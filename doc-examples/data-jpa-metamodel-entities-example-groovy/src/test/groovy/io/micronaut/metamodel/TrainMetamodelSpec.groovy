package io.micronaut.metamodel

import io.micronaut.data.tck.entities.Train
import io.micronaut.data.tck.entities.TrainManufacturer
import io.micronaut.data.tck.entities.TrainSpecs
import io.micronaut.data.tck.entities.Train_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute

class TrainMetamodelSpec extends AbstractEntityMetamodelSpec {

    final def TRAIN_CLASS_NAME = Train.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                Train,
                Train_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [Long], TRAIN_CLASS_NAME),
                        new Attribute("name", SingularAttribute, [String], TRAIN_CLASS_NAME),
                        new Attribute("model", SingularAttribute, [String], TRAIN_CLASS_NAME),

                        new Attribute("capacity", SingularAttribute, [Integer], TRAIN_CLASS_NAME),
                        new Attribute("speed", SingularAttribute, [Double], TRAIN_CLASS_NAME),
                        new Attribute("electric", SingularAttribute, [Boolean], TRAIN_CLASS_NAME),

                        new Attribute("departureTime", SingularAttribute, [LocalDateTime], TRAIN_CLASS_NAME),
                        new Attribute("createdAt", SingularAttribute, [Instant], TRAIN_CLASS_NAME),
                        new Attribute("departureDate", SingularAttribute, [LocalDate], TRAIN_CLASS_NAME),
                        new Attribute("departureTimeOnly", SingularAttribute, [LocalTime], TRAIN_CLASS_NAME),

                        new Attribute("specs", SingularAttribute, [TrainSpecs], TRAIN_CLASS_NAME),
                        new Attribute("manufacturer", SingularAttribute, [TrainManufacturer], TRAIN_CLASS_NAME),
                ),
                List.of()
        )
    }
}
