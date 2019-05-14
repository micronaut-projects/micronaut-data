/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.intercept;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.intercept.*;
import io.micronaut.data.runtime.datastore.Datastore;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;

/**
 * Factory for creating the different types of interceptors.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class PredatorInterceptorFactory {

    /**
     * Default constructor.
     */
    public PredatorInterceptorFactory() {
        ConversionService<?> conversionService = ConversionService.SHARED;
        conversionService.addConverter(OffsetDateTime.class, java.sql.Date.class, offsetDateTime ->
                new java.sql.Date(offsetDateTime.toInstant().toEpochMilli())
        );
        conversionService.addConverter(OffsetDateTime.class, Date.class, offsetDateTime ->
                new Date(offsetDateTime.toInstant().toEpochMilli())
        );
        conversionService.addConverter(OffsetDateTime.class, Instant.class, OffsetDateTime::toInstant);
        conversionService.addConverter(OffsetDateTime.class, Long.class, offsetDateTime ->
                offsetDateTime.toInstant().toEpochMilli()
        );
        conversionService.addConverter(OffsetDateTime.class, Timestamp.class, offsetDateTime ->
                new Timestamp(offsetDateTime.toInstant().toEpochMilli())
        );
    }

    /**
     * Creates the {@link FindOneInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link FindOneInterceptor}
     */
    @EachBean(Datastore.class)
    protected FindOneInterceptor findOneInterceptor(Datastore datastore) {
        return new DefaultFindOneInterceptor(datastore);
    }

    /**
     * Creates the {@link FindByIdInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link FindByIdInterceptor}
     */
    @EachBean(Datastore.class)
    protected FindByIdInterceptor findByIdInterceptor(Datastore datastore) {
        return new DefaultFindByIdInterceptor(datastore);
    }


    /**
     * Creates the {@link FindPageInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link FindPageInterceptor}
     */
    @EachBean(Datastore.class)
    protected FindPageInterceptor findPageInterceptor(Datastore datastore) {
        return new DefaultFindPageInterceptor(datastore);
    }

    /**
     * Creates the {@link FindSliceInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link FindSliceInterceptor}
     */
    @EachBean(Datastore.class)
    protected FindSliceInterceptor findSliceInterceptor(Datastore datastore) {
        return new DefaultFindSliceInterceptor(datastore);
    }

    /**
     * Creates the {@link FindStreamInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link FindStreamInterceptor}
     */
    @EachBean(Datastore.class)
    protected FindStreamInterceptor findStreamInterceptor(Datastore datastore) {
        return new DefaultFindStreamInterceptor(datastore);
    }

    /**
     * Creates the {@link FindOptionalInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link FindOptionalInterceptor}
     */
    @EachBean(Datastore.class)
    protected FindOptionalInterceptor findOptionalInterceptor(Datastore datastore) {
        return new DefaultFindOptionalInterceptor(datastore);
    }

    /**
     * Creates the {@link FindAllInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link FindAllInterceptor}
     */
    @EachBean(Datastore.class)
    protected FindAllInterceptor findAllInterceptor(Datastore datastore) {
        return new DefaultFindAllInterceptor(datastore);
    }

    /**
     * Creates the {@link DeleteAllInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link DeleteAllInterceptor}
     */
    @EachBean(Datastore.class)
    protected DeleteAllInterceptor deleteAllInterceptor(Datastore datastore) {
        return new DefaultDeleteAllInterceptor(datastore);
    }

    /**
     * Creates the {@link UpdateInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link UpdateInterceptor}
     */
    @EachBean(Datastore.class)
    protected UpdateInterceptor updateInterceptor(Datastore datastore) {
        return new DefaultUpdateInterceptor(datastore);
    }

    /**
     * Creates the {@link DeleteOneInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link DeleteOneInterceptor}
     */
    @EachBean(Datastore.class)
    protected DeleteOneInterceptor deleteOneInterceptor(Datastore datastore) {
        return new DefaultDeleteOneInterceptor(datastore);
    }

    /**
     * Creates the {@link CountInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link CountInterceptor}
     */
    @EachBean(Datastore.class)
    protected CountInterceptor countInterceptor(Datastore datastore) {
        return new DefaultCountInterceptor(datastore);
    }

    /**
     * Creates the {@link io.micronaut.data.intercept.SaveEntityInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link SaveEntityInterceptor}
     */
    @EachBean(Datastore.class)
    protected SaveEntityInterceptor saveEntityInterceptor(Datastore datastore) {
        return new DefaultSaveEntityInterceptor(datastore);
    }

    /**
     * Creates the {@link io.micronaut.data.intercept.SaveAllInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link SaveAllInterceptor}
     */
    @EachBean(Datastore.class)
    protected SaveAllInterceptor saveAllInterceptor(Datastore datastore) {
        return new DefaultSaveAllInterceptor(datastore);
    }

    /**
     * Creates the {@link io.micronaut.data.intercept.ExistsByInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link ExistsByInterceptor}
     */
    @EachBean(Datastore.class)
    protected ExistsByInterceptor existsByInterceptor(Datastore datastore) {
        return new DefaultExistsByInterceptor(datastore);
    }
}
