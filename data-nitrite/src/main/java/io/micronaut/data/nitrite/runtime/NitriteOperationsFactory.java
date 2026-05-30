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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.nitrite.conf.NitriteConfiguration;
import jakarta.inject.Singleton;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.NitriteBuilder;
import org.dizitart.no2.common.module.NitriteModule;
import org.dizitart.no2.mapper.jackson.JacksonMapperModule;
import org.dizitart.no2.mvstore.MVStoreModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Factory for creating the {@link Nitrite} database instance.
 *
 * @since 1.0.0
 */
@Factory
public class NitriteOperationsFactory {

  private static final Logger LOG = LoggerFactory.getLogger(NitriteOperationsFactory.class);

  private static final String ROCKSDB_MODULE_CLASS = "org.dizitart.no2.rocksdb.RocksDBModule";
  private static final String SPATIAL_MODULE_CLASS = "org.dizitart.no2.spatial.SpatialModule";
  private static final String JTS_MODULE_CLASS = "com.bedatadriven.jackson.datatype.jts.JtsModule";

  /**
   * Creates the {@link Nitrite} database instance.
   *
   * @param config the Nitrite configuration
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

    // Load Spatial module if present on classpath
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
                Object builder = rocksDbModuleClass.getMethod("withConfig").invoke(null);
                builder = builder.getClass().getMethod("filePath", File.class).invoke(builder, file);
                return (NitriteModule) builder.getClass().getMethod("build").invoke(builder);
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

  private NitriteModule createJacksonMapperModule() {
    // Check if JTS module is available for Geometry serialization
    if (ClassUtils.isPresent(JTS_MODULE_CLASS, null)) {
        try {
            Class<?> jtsModuleClass = Class.forName(JTS_MODULE_CLASS);
            Object jtsModule = jtsModuleClass.getDeclaredConstructor().newInstance();
            // Create JacksonMapperModule with both JavaTimeModule and JtsModule
            return new JacksonMapperModule(new JavaTimeModule(), (com.fasterxml.jackson.databind.Module) jtsModule);
        } catch (Exception e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("JTS module found but could not be registered: {}", e.getMessage());
            }
        }
    }
    // Fall back to just JavaTimeModule
    return new JacksonMapperModule(new JavaTimeModule());
  }

  private File prepareDbFile(String dbPath) {
    File file = Path.of(dbPath).toFile();
    File parent = file.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new RuntimeException("Could not create directory " + parent);
    }
    return file;
  }
}
