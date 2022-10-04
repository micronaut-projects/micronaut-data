/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.cosmos.annotation;

import io.micronaut.core.annotation.Introspected;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to declare Cosmos Azure DB specific properties for the container.
 *
 * @author radovanradic
 * @since 3.8.0
 */
@Introspected
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Documented
public @interface Container {

    /**
     * @return the container name
     */
    String name() default "";

    /**
     * Used to set partition key path for the container. If {@link PartitionKey} is declared on any field it will have higher priority and will be used
     * as partition key for the container.
     *
     * @return the partition key path
     */
    String partitionKeyPath() default "";

    /**
     * @return throughput request units for the entity (container). If less or equal to 0 then not used
     */
    int throughputRequestUnits() default 0;

    /**
     * @return an indicator telling whether throughput is auto-scaled for the entity (container)
     */
    boolean throughputAutoScale() default false;
}
