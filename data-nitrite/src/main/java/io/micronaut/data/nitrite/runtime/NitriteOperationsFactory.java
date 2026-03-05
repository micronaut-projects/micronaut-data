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

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.nitrite.conf.NitriteConfiguration;
import io.micronaut.data.nitrite.operations.NitriteRepositoryOperations;
import io.micronaut.data.nitrite.transaction.NitriteTransactionHolder;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import jakarta.inject.Singleton;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.NitriteBuilder;
import org.dizitart.no2.common.module.NitriteModule;
import org.dizitart.no2.mapper.jackson.JacksonMapperModule;
import org.dizitart.no2.mvstore.MVStoreModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Factory for NitriteDB beans. */
@Factory
@Internal
public final class NitriteOperationsFactory {

  private static final Logger LOG = LoggerFactory.getLogger(NitriteOperationsFactory.class);

  private static final String ROCKSDB_MODULE_CLASS = "org.dizitart.no2.rocksdb.RocksDBModule";
  private static final String SPATIAL_MODULE_CLASS = "org.dizitart.no2.spatial.SpatialModule";

  /**
   * Create a Nitrite database instance.
   *
   * <p>Note: the mapper is configured with Jackson's {@link JavaTimeModule}. The runtime normalizes
   * certain values (notably {@link java.time.Instant}) to match the mapper’s stored representation
   * when building query filters (see {@code DefaultNitriteRepositoryOperations.toFilterValue}).
   *
   * @param config the configuration
   * @return the database
   */
  @Bean
  @Singleton
  public Nitrite nitriteDatabase(NitriteConfiguration config) {
    String dbPath = config.getDbPath();
    NitriteConfiguration.StorageMode mode = config.getStorageMode();
    NitriteBuilder builder = Nitrite.builder();

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

    builder.loadModule(new JacksonMapperModule(new JavaTimeModule()));
    
    // Load Spatial module if present on classpath
    loadSpatialModule().ifPresent(builder::loadModule);
    
    builder.fieldSeparator(config.getFieldSeparator());

    String username = config.getUsername();
    String password = config.getPassword();
    if (username != null && password != null) {
      return builder.openOrCreate(username, password);
    }
    return builder.openOrCreate();
  }

  private NitriteModule loadRocksDbModule(File file) {
    if (ClassUtils.isPresent(ROCKSDB_MODULE_CLASS, null)) {
        // Use reflection to avoid hard dependency
        try {
            Class<?> rocksDbModuleClass = Class.forName(ROCKSDB_MODULE_CLASS);
            Object config = rocksDbModuleClass.getMethod("withConfig").invoke(null);
            config.getClass().getMethod("filePath", File.class).invoke(config, file);
            return (NitriteModule) config.getClass().getMethod("build").invoke(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize RocksDB module even though it is on the classpath", e);
        }
    }
    throw new IllegalStateException("RocksDB storage mode requested but 'nitrite-rocksdb-adapter' is not on the classpath.");
  }

  private Optional<NitriteModule> loadSpatialModule() {
    if (ClassUtils.isPresent(SPATIAL_MODULE_CLASS, null)) {
        try {
            Class<?> spatialModuleClass = Class.forName(SPATIAL_MODULE_CLASS);
            return Optional.of((NitriteModule) spatialModuleClass.getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Spatial module found on classpath but could not be initialized: {}", e.getMessage());
            }
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
   * Create a Nitrite repository operations instance.
   *
   * @param database the database
   * @param configuration the configuration
   * @param dateTimeProvider the date time provider
   * @param runtimeEntityRegistry the runtime entity registry
   * @param conversionService the conversion service
   * @param attributeConverterRegistry the attribute converter registry
   * @param transactionHolder the transaction holder
   * @return the repository operations
   */
  @Bean
  @Primary
  @Singleton
  public NitriteRepositoryOperations nitriteRepositoryOperations(
      Nitrite database,
      NitriteConfiguration configuration,
      DateTimeProvider dateTimeProvider,
      RuntimeEntityRegistry runtimeEntityRegistry,
      DataConversionService conversionService,
      AttributeConverterRegistry attributeConverterRegistry,
      NitriteTransactionHolder transactionHolder) {
    return new DefaultNitriteRepositoryOperations(
        database,
        configuration,
        dateTimeProvider,
        runtimeEntityRegistry,
        conversionService,
        attributeConverterRegistry,
        transactionHolder);
  }
}
