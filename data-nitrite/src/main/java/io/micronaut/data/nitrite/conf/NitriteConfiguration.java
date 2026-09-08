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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.Named;

/**
 * Configuration properties for a named NitriteDB datasource.
 *
 * <p>Example:
 *
 * <pre>
 * micronaut:
 *   nitrite:
 *     default:
 *       db-path: /data/myapp.db
 *       storage-mode: MVSTORE
 *       create-indexes: true
 *       sorted-read-strategy: AUTO
 *       username: admin
 *       password: secret
 * </pre>
 */
public class NitriteConfiguration implements Named {

  /** Configuration prefix used for binding named Nitrite settings. */
  public static final String PREFIX = "micronaut.nitrite";

  /**
   * Name of the implicit datasource. It is the primary configuration, so its per-datasource beans
   * are resolvable without a name qualifier; every other datasource is named and qualified.
   */
  public static final String DEFAULT_NAME = "default";

  private final String name;

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
   * Strategy for applying limits to sorted reads. Defaults to {@code AUTO}, which selects the
   * strategy measured to be best for the configured storage mode.
   */
  private SortedReadStrategy sortedReadStrategy = SortedReadStrategy.AUTO;

  /**
   * The field separator character used for nested properties. Defaults to {@code "."}.
   */
  private String fieldSeparator = ".";

  /**
   * Whether to automatically create indexes based on {@code @Index} annotations and scalar
   * identity properties.
   * Defaults to {@code true}.
   */
  private boolean createIndexes = true;

  /**
   * The MVStore page split size in bytes, or {@code null} to leave the adapter's own default in
   * place. Only read when the storage mode is {@code MVSTORE}.
   *
   * <p>Nitrite 5.3.0 defaults this to {@code 16} <em>bytes</em> where its javadoc promises 16 KB,
   * so a leaf page splits as soon as it holds more than one entry. The resulting tree is deep and
   * page-heavy - upstream measured 93,781 pages at depth 13 for 46,926 entries, against 36,096 at
   * depth 12 with MVStore's own 16 KB default - which makes the cost of a write climb with the
   * size of the collection. Setting this to {@code 16384} restores the documented default; H2
   * clamps it to {@code (cacheSize / cacheConcurrency) >> 4}, so 64 KB is the practical ceiling.
   *
   * <p>An existing file keeps the page shape it was written with until those pages are rewritten.
   */
  @Nullable private Integer mvstorePageSplitSize;

  /** Optional username for authenticated databases. */
  @Nullable private String username;

  /** Optional password for authenticated databases. */
  @Nullable private String password;

  /**
   * Default constructor.
   *
   * @param name The configuration name
   */
  public NitriteConfiguration(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
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
   * Returns the sorted-read limit strategy.
   * @return the sorted-read limit strategy
   */
  public SortedReadStrategy getSortedReadStrategy() {
    return sortedReadStrategy;
  }

  /**
   * Sets the sorted-read limit strategy.
   * @param sortedReadStrategy the sorted-read limit strategy
   */
  public void setSortedReadStrategy(SortedReadStrategy sortedReadStrategy) {
    this.sortedReadStrategy = sortedReadStrategy;
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
   * Returns the MVStore page split size in bytes.
   * @return the page split size, or {@code null} to use the adapter default
   */
  @Nullable
  public Integer getMvstorePageSplitSize() {
    return mvstorePageSplitSize;
  }

  /**
   * Sets the MVStore page split size in bytes.
   * @param mvstorePageSplitSize the page split size, or {@code null} to use the adapter default
   */
  public void setMvstorePageSplitSize(@Nullable final Integer mvstorePageSplitSize) {
    this.mvstorePageSplitSize = mvstorePageSplitSize;
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

  /**
   * Controls whether sorted row limits are applied by Nitrite or by the returned cursor.
   */
  public enum SortedReadStrategy {
    /** Select the measured strategy for the configured storage mode. */
    AUTO,
    /** Remove the limit from the Nitrite find and apply it while reading the cursor. */
    CURSOR,
    /** Keep the limit on the Nitrite find. */
    DATABASE;

    /**
     * Determines whether the sorted limit should be moved to the cursor.
     *
     * @param storageMode the configured storage mode
     * @return true when the cursor should apply the limit
     */
    public boolean usesCursorLimit(StorageMode storageMode) {
      return switch (this) {
        case AUTO -> storageMode != StorageMode.ROCKSDB;
        case CURSOR -> true;
        case DATABASE -> false;
      };
    }
  }
}
