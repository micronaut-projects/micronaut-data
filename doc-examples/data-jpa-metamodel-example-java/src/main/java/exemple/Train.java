/*
 * Copyright 2017-2026 original authors
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package exemple;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
public record Train(
    @Id
    long id,
    String name,
    String model,
    Integer capacity,
    Double speed,
    Boolean electric,
    LocalDateTime departureTime,
    Instant createdAt,
    LocalDate departureDate,
    LocalTime departureTimeOnly,
    MicronautRecord micronautRecord,
    @Transient
    String transientField,
    List<String> seats,
    Set<Integer> set,
    Collection<Double> collection,
    Map<String, String> map
) {
    public record MicronautRecord(int primitive) {
    }
}
