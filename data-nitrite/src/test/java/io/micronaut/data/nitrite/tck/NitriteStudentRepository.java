package io.micronaut.data.nitrite.tck;

import io.micronaut.data.document.tck.repositories.StudentRepository;
import io.micronaut.data.nitrite.annotation.NitriteRepository;

@NitriteRepository
public interface NitriteStudentRepository extends StudentRepository {
}
