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
package io.micronaut.data.nitrite.model;

import io.micronaut.core.annotation.Introspected;

/**
 * Non-{@code Serializable} POJO used to exercise the {@code INTROSPECTED_POJO} dispatch
 * strategy in the entity mapper. The {@code @Introspected} annotation causes a
 * compile-time {@link io.micronaut.core.beans.BeanIntrospection} to be generated,
 * which the mapper uses directly — no Serde codec and no {@code Serializable} required.
 */
@Introspected
public class CustomTag {

    private String key;
    private String value;

    /** Default constructor required by Serde. */
    public CustomTag() {
    }

    /**
     * Creates a tag with the given key and value.
     *
     * @param key the tag key
     * @param value the tag value
     */
    public CustomTag(String key, String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Returns the key.
     *
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the key.
     *
     * @param key the key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Returns the value.
     *
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value.
     *
     * @param value the value
     */
    public void setValue(String value) {
        this.value = value;
    }
}
