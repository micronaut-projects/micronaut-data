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
package io.micronaut.data.jdbc.h2.jsoncreator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The ISO 3166-2 value object of issue #3752: deserialized from a single JSON string ("US-NY") through
 * {@code @JsonCreator}, serialized back to that same string through {@code @JsonValue}, but persisted as two
 * separate columns.
 *
 * <p>The introspection exposes a single creator and Jackson claims it here, so Micronaut Data cannot use it to
 * map the two persisted columns. It instantiates through the no-argument constructor and the setters instead.</p>
 */
@Embeddable
@Serdeable
public class Country {

    private String countryCode;

    @Nullable
    private String regionCode;

    public Country() {
    }

    @JsonCreator
    public Country(String value) {
        this.countryCode = value.substring(0, 2);
        this.regionCode = value.length() > 3 ? value.substring(3) : null;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    @Nullable
    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(@Nullable String regionCode) {
        this.regionCode = regionCode;
    }

    @Override
    @JsonValue
    public String toString() {
        return countryCode + (regionCode != null ? "-" + regionCode : "");
    }
}
