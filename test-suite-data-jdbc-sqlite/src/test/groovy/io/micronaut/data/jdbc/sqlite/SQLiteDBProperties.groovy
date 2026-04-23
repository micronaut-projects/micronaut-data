/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.jdbc.sqlite

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Target([ElementType.ANNOTATION_TYPE, ElementType.TYPE])
@Retention(RetentionPolicy.RUNTIME)
@interface SQLiteDBProperties {
    String name() default "mydb"

    String packages() default "io.micronaut.data.tck.entities,io.micronaut.data.tck.jdbc.entities,io.micronaut.data.jdbc.sqlite"

    String schemaGenerate() default "CREATE_DROP"

    String dialect() default "ANSI"

    String dbType() default "sqlite"

    String driverClassName() default "org.sqlite.JDBC"

    String url() default "jdbc:sqlite:file:%s?mode=memory&cache=shared&foreign_keys=ON&busy_timeout=5000"

    String username() default ""

    String password() default ""
}
