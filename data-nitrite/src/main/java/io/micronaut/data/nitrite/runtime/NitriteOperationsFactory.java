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
package io.micronaut.data.nitrite.runtime;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.nitrite.conf.NitriteConfiguration;
import io.micronaut.data.nitrite.operations.NitriteRepositoryOperations;
import io.micronaut.data.nitrite.transaction.NitriteTransactionHolder;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.NitriteBuilder;
import org.dizitart.no2.common.module.NitriteModule;
import org.dizitart.no2.mvstore.MVStoreModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Factory for Nitrite repository operations.
 *
 * @since 1.0.0
 */
@Factory
@Internal
public final class NitriteOperationsFactory {

  private static final Logger LOG = LoggerFactory.getLogger(NitriteOperationsFactory.class);
  private static final String ROCKSDB_MODULE_CLASS = "org.dizitart.no2.rocksdb.RocksDBModule";
  private static final String SPATIAL_MODULE_CLASS = "org.dizitart.no2.spatial.SpatialModule";

  NitriteOperationsFactory() {
  }

  /**
   * Create a Nitrite database instance.
   *
   * <p>No {@code NitriteMapper} module is loaded explicitly; Nitrite falls back to its built-in
   * {@code SimpleNitriteMapper}. This module only calls Nitrite's {@code NitriteCollection} API
   * (not {@code ObjectRepository}), and {@code SimpleNitriteMapper.tryConvert} passes through any
   * value that already implements the requested target type — true for the {@link Comparable}
   * check performed by filter value normalization (see {@code FieldBasedFilter}) for common types
   * like {@link java.time.Instant}, so no Jackson-based mapper is required.
   *
   * @param config the configuration
   * @return the Nitrite database instance
   */
  @Bean(preDestroy = "close")
  @Singleton
  public Nitrite nitrite(@NonNull final NitriteConfiguration config) {
    NitriteBuilder builder = Nitrite.builder();

    NitriteConfiguration.StorageMode mode = config.getStorageMode();
    String dbPath = config.getDbPath();

    if (mode == NitriteConfiguration.StorageMode.IN_MEMORY) {
      LOG.info("Nitrite configured for pure in-memory storage.");
    } else if (StringUtils.isEmpty(dbPath)) {
      if (mode == NitriteConfiguration.StorageMode.ROCKSDB) {
        throw new IllegalStateException("RocksDB storage mode requires a valid nitrite.db-path.");
      }
      LOG.info("No nitrite.db-path provided, falling back to Nitrite pure in-memory storage.");
    } else {
      File file = prepareDbFile(dbPath);
      NitriteModule storeModule;
      if (mode == NitriteConfiguration.StorageMode.ROCKSDB) {
        storeModule = loadRocksDbModule(file);
      } else {
        storeModule = MVStoreModule.withConfig().filePath(file).build();
      }
      builder.loadModule(storeModule);
    }

    loadSpatialModule().ifPresent(builder::loadModule);

    builder.fieldSeparator(config.getFieldSeparator());

    String username = config.getUsername();
    String password = config.getPassword();
    if (username != null && password != null) {
      return builder.openOrCreate(username, password);
    }
    return builder.openOrCreate();
  }

  private @Nullable NitriteModule loadRocksDbModule(File file) {
    if (ClassUtils.isPresent(ROCKSDB_MODULE_CLASS, null)) {
      try {
        Class<?> rocksDbModuleClass = Class.forName(ROCKSDB_MODULE_CLASS);
        Object builderObj = rocksDbModuleClass.getMethod("withConfig").invoke(null);
        builderObj = builderObj.getClass().getMethod("filePath", File.class).invoke(builderObj, file);
        return (NitriteModule) builderObj.getClass().getMethod("build").invoke(builderObj);
      } catch (Exception e) {
        throw new RuntimeException("Failed to initialize RocksDB module even though it is on the classpath", e);
      }
    }
    return null;
  }

  private Optional<NitriteModule> loadSpatialModule() {
    if (ClassUtils.isPresent(SPATIAL_MODULE_CLASS, null)) {
      try {
        Class<?> spatialModuleClass = Class.forName(SPATIAL_MODULE_CLASS);
        return Optional.of((NitriteModule) spatialModuleClass.getDeclaredConstructor().newInstance());
      } catch (Exception e) {
        LOG.warn("Spatial module found on classpath but could not be initialized: {}", e.getMessage());
      }
    }
    return Optional.empty();
  }

  private File prepareDbFile(String dbPath) {
    File file = Path.of(dbPath).toFile();
    File parent = file.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new RuntimeException("Could not create directory " + parent);
    }
    return file;
  }

  /**
   * Creates {@link NitriteRepositoryOperations}.
   *
   * @param database the database
   * @param configuration the configuration
   * @param dateTimeProvider the date time provider
   * @param runtimeEntityRegistry the runtime entity registry
   * @param conversionService the conversion service (for field-level conversions)
   * @param attributeConverterRegistry the attribute converter registry
   * @param transactionHolder the transaction holder
   * @param serdeObjectMapper the optional Micronaut Serde ObjectMapper
   * @return the repository operations
   */
  @Bean
  @Primary
  @Singleton
  public NitriteRepositoryOperations nitriteRepositoryOperations(
      Nitrite database,
      NitriteConfiguration configuration,
      DateTimeProvider<Object> dateTimeProvider,
      RuntimeEntityRegistry runtimeEntityRegistry,
      DataConversionService conversionService,
      AttributeConverterRegistry attributeConverterRegistry,
      NitriteTransactionHolder transactionHolder,
      @Nullable ObjectMapper serdeObjectMapper) {
    return new DefaultNitriteRepositoryOperations(
        database,
        configuration,
        dateTimeProvider,
        runtimeEntityRegistry,
        conversionService,
        attributeConverterRegistry,
        transactionHolder,
        serdeObjectMapper);
  }
}
