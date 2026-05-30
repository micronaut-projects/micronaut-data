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

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.nitrite.conf.NitriteConfiguration;
import io.micronaut.data.nitrite.operations.NitriteRepositoryOperations;
import io.micronaut.data.nitrite.transaction.NitriteTransactionHolder;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import jakarta.inject.Singleton;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.NitriteBuilder;
import org.dizitart.no2.common.module.NitriteModule;
import org.dizitart.no2.mapper.jackson.JacksonMapper;
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
  private static final String GEOMETRY_MODULE_CLASS = "org.dizitart.no2.spatial.jackson.GeometryModule";

  NitriteOperationsFactory() {
  }

  /**
   * Create a Nitrite database instance.
   *
   * <p>Note: the mapper is configured with Jackson's {@link JavaTimeModule}. The runtime normalizes
   * certain values (notably {@link java.time.Instant}) to match the mapper's stored representation
   * when building query filters (see {@code DefaultNitriteRepositoryOperations.toFilterValue}).
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

    builder.loadModule(createJacksonMapperModule());

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
        if (LOG.isWarnEnabled()) {
          LOG.warn("Spatial module found on classpath but could not be initialized: {}", e.getMessage());
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Creates a NitriteModule with a Nitrite-internal Jackson ObjectMapper.
   *
   * <p><strong>Architecture Note:</strong> Jackson is an internal implementation detail of the
   * Nitrite integration, not part of this module's public API. Entity ↔ Map conversion at the
   * module boundary is handled by {@code NitriteEntityMapper} using Micronaut Serde. This
   * Jackson ObjectMapper is used only for Nitrite's internal Document storage.</p>
   *
   * <p>This factory method centralizes Jackson configuration in a single place to ensure:</p>
   * <ul>
   *   <li>Consistent date handling for any raw temporal values Nitrite stores internally</li>
   *   <li>Proper NitriteId serialization via NitriteIdModule</li>
   *   <li>Optional JTS module support for Geometry types</li>
   * </ul>
   *
   * @return NitriteModule with configured internal Jackson mapper
   */
  private NitriteModule createJacksonMapperModule() {
    ObjectMapper mapper = new ObjectMapper();

    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.registerModule(new JavaTimeModule());

    try {
      Class<?> nitriteIdModuleClass = Class.forName("org.dizitart.no2.mapper.jackson.modules.NitriteIdModule");
      mapper.registerModule((Module) nitriteIdModuleClass.getDeclaredConstructor().newInstance());
    } catch (Exception e) {
      if (LOG.isWarnEnabled()) {
        LOG.warn("NitriteIdModule found but could not be registered: {}", e.getMessage());
      }
    }

    if (ClassUtils.isPresent(GEOMETRY_MODULE_CLASS, null)) {
      try {
        Class<?> geometryModuleClass = Class.forName(GEOMETRY_MODULE_CLASS);
        Object geometryModule = geometryModuleClass.getDeclaredConstructor().newInstance();
        mapper.registerModule((Module) geometryModule);
        if (LOG.isDebugEnabled()) {
          LOG.debug("GeometryModule registered for JTS Geometry serialization");
        }
      } catch (Exception e) {
        if (LOG.isWarnEnabled()) {
          LOG.warn("GeometryModule found but could not be registered: {}", e.getMessage());
        }
      }
    }

    return new NitriteJacksonMapperModule(mapper);
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
   * @param serdeObjectMapper the Micronaut Serde ObjectMapper (for entity ↔ Map conversion at boundary)
   * @return the repository operations
   */
  @Bean
  @Singleton
  public NitriteRepositoryOperations nitriteRepositoryOperations(
      Nitrite database,
      NitriteConfiguration configuration,
      DateTimeProvider<Object> dateTimeProvider,
      io.micronaut.data.model.runtime.RuntimeEntityRegistry runtimeEntityRegistry,
      DataConversionService conversionService,
      AttributeConverterRegistry attributeConverterRegistry,
      NitriteTransactionHolder transactionHolder,
      io.micronaut.serde.ObjectMapper serdeObjectMapper) {
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

  /**
   * NitriteModule wrapping a locally-constructed, Nitrite-internal Jackson ObjectMapper.
   * This is not Micronaut's ObjectMapper bean — it is a dedicated instance configured
   * with configured date handling and optional spatial type support, used only inside Nitrite.
   */
  private static class NitriteJacksonMapperModule implements NitriteModule {
    private final JacksonMapper jacksonMapper;

    NitriteJacksonMapperModule(ObjectMapper nitriteMapper) {
      this.jacksonMapper = new JacksonMapper() {
        @Override
        public ObjectMapper getObjectMapper() {
          return nitriteMapper;
        }
      };
    }

    @Override
    public java.util.Set<org.dizitart.no2.common.module.NitritePlugin> plugins() {
      return java.util.Collections.singleton(jacksonMapper);
    }
  }
}
