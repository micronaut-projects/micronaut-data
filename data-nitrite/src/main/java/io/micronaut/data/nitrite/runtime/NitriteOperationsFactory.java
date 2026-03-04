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
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.NitriteBuilder;
import org.dizitart.no2.common.module.NitriteModule;
import org.dizitart.no2.mapper.jackson.JacksonMapperModule;
import org.dizitart.no2.mvstore.MVStoreModule;
import org.dizitart.no2.rocksdb.RocksDBModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Factory for NitriteDB beans. */
@Factory
@Internal
public final class NitriteOperationsFactory {

  private static final Logger LOG = LoggerFactory.getLogger(NitriteOperationsFactory.class);

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
        storeModule = RocksDBModule.withConfig().filePath(file).build();
      } else {
        storeModule = MVStoreModule.withConfig().filePath(file).build();
      }
      builder.loadModule(storeModule);
    }

    builder.loadModule(new JacksonMapperModule(new JavaTimeModule()));
    builder.fieldSeparator(config.getFieldSeparator());

    String username = config.getUsername();
    String password = config.getPassword();
    if (username != null && password != null) {
      return builder.openOrCreate(username, password);
    }
    return builder.openOrCreate();
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
      DateTimeProvider dateTimeProvider,
      RuntimeEntityRegistry runtimeEntityRegistry,
      DataConversionService conversionService,
      AttributeConverterRegistry attributeConverterRegistry,
      NitriteTransactionHolder transactionHolder) {
    return new DefaultNitriteRepositoryOperations(
        database,
        dateTimeProvider,
        runtimeEntityRegistry,
        conversionService,
        attributeConverterRegistry,
        transactionHolder);
  }
}
