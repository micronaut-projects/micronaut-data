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
package io.micronaut.data.nitrite.runtime.mapping;

/**
 * One {@code @JoinColumn} entry of a composite foreign key: the local document field that stores
 * the value, and the name of the property on the associated entity it mirrors.
 *
 * @param localName          the local document field name ({@code @JoinColumn(name = ...)})
 * @param referencedProperty the property name on the associated entity ({@code referencedColumnName})
 */
public record CompositeJoinColumn(String localName, String referencedProperty) {
}
