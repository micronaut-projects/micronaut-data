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

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

/**
 * Configuration for a named Nitrite datasource.
 *
 * @since 5.2.0
 */
@EachProperty(value = NitriteConfiguration.PREFIX, primary = NitriteConfiguration.DEFAULT_NAME)
public final class NamedNitriteConfiguration extends NitriteConfiguration {

  /**
   * Create a new named configuration.
   *
   * @param name the datasource name
   */
  public NamedNitriteConfiguration(@Parameter String name) {
    super(name);
  }
}
