package io.micronaut.data.hibernate.jakarta_data.entity;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;

/**
 * A repository that inherits from the built-in BasicRepository and adds no methods.
 */
@Repository
public interface Boxes extends BasicRepository<Box, String> {
}
