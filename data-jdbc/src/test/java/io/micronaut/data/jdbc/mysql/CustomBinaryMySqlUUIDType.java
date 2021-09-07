package io.micronaut.data.jdbc.mysql;

import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Documented
@TypeDef(type = DataType.BYTE_ARRAY, converter = MySqlUUIDBinaryConverter.class)
@MappedProperty(definition = "binary(16)")
public @interface CustomBinaryMySqlUUIDType {
}
