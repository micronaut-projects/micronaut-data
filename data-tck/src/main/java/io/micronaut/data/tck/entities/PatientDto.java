package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;

import java.util.List;

@Introspected
public class PatientDto {

    private final String name;

    private final String history;

    private final String doctorNotes;

    @TypeDef(type = DataType.JSON)
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
