/**
 * Contains Spring Data Specific integrations
 */
@Configuration
@Requires(classes = Repository.class)
package io.micronaut.data.runtime.spring;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
import org.springframework.data.repository.Repository;