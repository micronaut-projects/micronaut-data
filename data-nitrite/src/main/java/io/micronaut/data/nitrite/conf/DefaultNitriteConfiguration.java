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
package io.micronaut.data.nitrite.conf;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;

/**
 * Default in-memory Nitrite configuration used when no datasource is configured.
 *
 * @since 5.2.0
 */
@Primary
@Requires(missingProperty = NitriteConfiguration.PREFIX)
@ConfigurationProperties(NitriteConfiguration.PREFIX + "." + NitriteConfiguration.DEFAULT_NAME)
public final class DefaultNitriteConfiguration extends NitriteConfiguration {

  /**
   * Creates the default configuration.
   */
  public DefaultNitriteConfiguration() {
    super(NitriteConfiguration.DEFAULT_NAME);
  }
}
