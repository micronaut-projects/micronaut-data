package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class PatientDto {

    private final String name;

    private final String history;

    private final String doctorNotes;

    public PatientDto(String name, String history, String doctorNotes) {
        this.name = name;
        this.history = history;
        this.doctorNotes = doctorNotes;
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
}
