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
package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.annotation.JsonRepresentation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;

import java.util.List;

@Introspected
public class PatientDto {

    private final String name;

    private final String history;

    private final String doctorNotes;

    @TypeDef(type = DataType.JSON)
    @JsonRepresentation(type = JsonDataType.BLOB)
    private final List<String> appointments;

    public PatientDto(String name, String history, String doctorNotes, @Nullable List<String> appointments) {
        this.name = name;
        this.history = history;
        this.doctorNotes = doctorNotes;
        this.appointments = appointments;
    }

    public String getName() {
        return name;
    }

    public String getHistory() {
        return history;
    }

    public String getDoctorNotes() {
        return doctorNotes;
    }

    public List<String> getAppointments() {
        return appointments;
    }
}
