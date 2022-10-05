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
package io.micronaut.data.cosmos.config;

/**
 * Throughput settings for database and container.
 */
public class ThroughputSettings {

    private Integer requestUnits;

    private boolean autoScale;

    /**
     * @return the request units
     */
    public Integer getRequestUnits() {
        return requestUnits;
    }

    /**
     * Sets the request units.
     *
     * @param requestUnits the request units
     */
    public void setRequestUnits(Integer requestUnits) {
        this.requestUnits = requestUnits;
    }

    /**
     * @return gets an indicator telling whether throughput is auto scaled
     */
    public boolean isAutoScale() {
        return autoScale;
    }

    /**
     * Sets the auto scaled indicator for throughput.
     *
     * @param autoScale auto scale value
     */
    public void setAutoScale(boolean autoScale) {
        this.autoScale = autoScale;
    }
}
