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
import io.micronaut.core.annotation.Nullable;

/**
 * Configuration properties for NitriteDB, bound from {@code nitrite.*} in application.yml.
 *
 * <p>Example:
 *
 * <pre>
 * nitrite:
 *   db-path: /data/myapp.db
 *   username: admin
 *   password: secret
 * </pre>
 */
@ConfigurationProperties(NitriteConfiguration.PREFIX)
public final class NitriteConfiguration {

  public static final String PREFIX = "nitrite";

  /**
   * Path to the NitriteDB file, or {@code "memory"} for in-memory mode. Defaults to {@code
   * "memory"}.
   */
  private String dbPath = "memory";

  /** Optional username for authenticated databases. */
  @Nullable private String username;

  /** Optional password for authenticated databases. */
  @Nullable private String password;

  /**
   * @return the db path
   */
  public String getDbPath() {
    return dbPath;
  }

  /**
   * @param dbPath the db path
   */
  public void setDbPath(final String dbPath) {
    this.dbPath = dbPath;
  }

  /**
   * @return the username
   */
  @Nullable
  public String getUsername() {
    return username;
  }

  /**
   * @param username the username
   */
  public void setUsername(@Nullable final String username) {
    this.username = username;
  }

  /**
   * @return the password
   */
  @Nullable
  public String getPassword() {
    return password;
  }

  /**
   * @param password the password
   */
  public void setPassword(@Nullable final String password) {
    this.password = password;
  }
}
