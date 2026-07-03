package io.micronaut.metamodel

import io.micronaut.data.tck.entities.BasicTypes
import io.micronaut.data.tck.entities.BasicTypes_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute

import java.nio.charset.Charset
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute

class BasicTypesMetamodelSpec extends AbstractEntityMetamodelSpec {

    final def BASIC_TYPES_CLASS_NAME = BasicTypes.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                BasicTypes,
                BasicTypes_,
                EntityType,
                List.of(
                        new Attribute("myId", SingularAttribute, [Long], BASIC_TYPES_CLASS_NAME),
                        new Attribute("primitiveInteger", SingularAttribute, [Integer], BASIC_TYPES_CLASS_NAME),
                        new Attribute("primitiveLong", SingularAttribute, [Long], BASIC_TYPES_CLASS_NAME),
                        new Attribute("primitiveBoolean", SingularAttribute, [Boolean], BASIC_TYPES_CLASS_NAME),
                        new Attribute("primitiveChar", SingularAttribute, [Character], BASIC_TYPES_CLASS_NAME),
                        new Attribute("primitiveShort", SingularAttribute, [Short], BASIC_TYPES_CLASS_NAME),
                        new Attribute("primitiveDouble", SingularAttribute, [Double], BASIC_TYPES_CLASS_NAME),
                        new Attribute("primitiveFloat", SingularAttribute, [Float], BASIC_TYPES_CLASS_NAME),
                        new Attribute("primitiveByte", SingularAttribute, [Byte], BASIC_TYPES_CLASS_NAME),
                        new Attribute("string", SingularAttribute, [String], BASIC_TYPES_CLASS_NAME),
                        new Attribute("charSequence", SingularAttribute, [CharSequence], BASIC_TYPES_CLASS_NAME),
                        new Attribute("wrapperInteger", SingularAttribute, [Integer], BASIC_TYPES_CLASS_NAME),
                        new Attribute("wrapperLong", SingularAttribute, [Long], BASIC_TYPES_CLASS_NAME),
                        new Attribute("wrapperBoolean", SingularAttribute, [Boolean], BASIC_TYPES_CLASS_NAME),
                        new Attribute("wrapperChar", SingularAttribute, [Character], BASIC_TYPES_CLASS_NAME),
                        new Attribute("wrapperShort", SingularAttribute, [Short], BASIC_TYPES_CLASS_NAME),
                        new Attribute("wrapperDouble", SingularAttribute, [Double], BASIC_TYPES_CLASS_NAME),
                        new Attribute("wrapperFloat", SingularAttribute, [Float], BASIC_TYPES_CLASS_NAME),
                        new Attribute("wrapperByte", SingularAttribute, [Byte], BASIC_TYPES_CLASS_NAME),
                        new Attribute("url", SingularAttribute, [URL], BASIC_TYPES_CLASS_NAME),
                        new Attribute("uri", SingularAttribute, [URI], BASIC_TYPES_CLASS_NAME),
                        new Attribute("byteArray", SingularAttribute, [byte[].class], BASIC_TYPES_CLASS_NAME),
                        new Attribute("date", SingularAttribute, [Date], BASIC_TYPES_CLASS_NAME),
                        new Attribute("localDateTime", SingularAttribute, [LocalDateTime], BASIC_TYPES_CLASS_NAME),
                        new Attribute("zonedDateTime", SingularAttribute, [ZonedDateTime], BASIC_TYPES_CLASS_NAME),
                        new Attribute("offsetDateTime", SingularAttribute, [OffsetDateTime], BASIC_TYPES_CLASS_NAME),
                        new Attribute("localDate", SingularAttribute, [LocalDate], BASIC_TYPES_CLASS_NAME),
                        new Attribute("localTime", SingularAttribute, [LocalTime], BASIC_TYPES_CLASS_NAME),
                        new Attribute("instant", SingularAttribute, [Instant], BASIC_TYPES_CLASS_NAME),
                        new Attribute("uuid", SingularAttribute, [UUID], BASIC_TYPES_CLASS_NAME),
                        new Attribute("bigDecimal", SingularAttribute, [BigDecimal], BASIC_TYPES_CLASS_NAME),
                        new Attribute("timeZone", SingularAttribute, [TimeZone], BASIC_TYPES_CLASS_NAME),
                        new Attribute("charset", SingularAttribute, [Charset], BASIC_TYPES_CLASS_NAME),
                        new Attribute("dateCreated", SingularAttribute, [Instant], BASIC_TYPES_CLASS_NAME),
                        new Attribute("dateUpdated", SingularAttribute, [Instant], BASIC_TYPES_CLASS_NAME),
                ),
                List.of()
        )
    }
}
