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
 *   storage-mode: MVSTORE
 *   create-indexes: true
 *   username: admin
 *   password: secret
 * </pre>
 */
@ConfigurationProperties(NitriteConfiguration.PREFIX)
public final class NitriteConfiguration {

  /** Configuration prefix used for binding Nitrite settings (e.g. {@code nitrite.db-path}). */
  public static final String PREFIX = "nitrite";

  /**
   * Path to the NitriteDB file. If not provided and storage-mode is MVSTORE,
   * an in-memory database is created.
   */
  @Nullable private String dbPath;

  /**
   * The storage mode to use. Defaults to {@code MVSTORE}.
   */
  private StorageMode storageMode = StorageMode.MVSTORE;

  /**
   * The field separator character used for nested properties. Defaults to {@code "."}.
   */
  private String fieldSeparator = ".";

  /**
   * Whether to automatically create indexes based on {@code @Index} annotations.
   * Defaults to {@code true}.
   */
  private boolean createIndexes = true;

  /** Optional username for authenticated databases. */
  @Nullable private String username;

  /** Optional password for authenticated databases. */
  @Nullable private String password;

  /**
   * Default constructor.
   */
  public NitriteConfiguration() {
  }

  /**
   * Returns the db path.
   * @return the db path
   */
  @Nullable
  public String getDbPath() {
    return dbPath;
  }

  /**
   * Sets the db path.
   * @param dbPath the db path
   */
  public void setDbPath(@Nullable final String dbPath) {
    this.dbPath = dbPath;
  }

  /**
   * Returns the username.
   * @return the username
   */
  @Nullable
  public String getUsername() {
    return username;
  }

  /**
   * Sets the username.
   * @param username the username
   */
  public void setUsername(@Nullable final String username) {
    this.username = username;
  }

  /**
   * Returns the password.
   * @return the password
   */
  @Nullable
  public String getPassword() {
    return password;
  }

  /**
   * Sets the password.
   * @param password the password
   */
  public void setPassword(@Nullable final String password) {
    this.password = password;
  }

  /**
   * Returns the storage mode.
   * @return the storage mode
   */
  public StorageMode getStorageMode() {
    return storageMode;
  }

  /**
   * Sets the storage mode.
   * @param storageMode the storage mode
   */
  public void setStorageMode(StorageMode storageMode) {
    this.storageMode = storageMode;
  }

  /**
   * Returns the field separator.
   * @return the field separator
   */
  public String getFieldSeparator() {
    return fieldSeparator;
  }

  /**
   * Sets the field separator.
   * @param fieldSeparator the field separator
   */
  public void setFieldSeparator(String fieldSeparator) {
    this.fieldSeparator = fieldSeparator;
  }

  /**
   * Returns whether to create indexes.
   * @return whether to create indexes
   */
  public boolean isCreateIndexes() {
    return createIndexes;
  }

  /**
   * Sets whether to create indexes.
   * @param createIndexes whether to create indexes
   */
  public void setCreateIndexes(boolean createIndexes) {
    this.createIndexes = createIndexes;
  }

  /**
   * Storage modes supported by NitriteDB.
   */
  public enum StorageMode {
    /**
     * MVStore-backed persistent database.
     */
    MVSTORE,
    /**
     * In-memory database.
     */
    IN_MEMORY,
    /**
     * RocksDB-backed persistent database.
     */
    ROCKSDB
  }
}
