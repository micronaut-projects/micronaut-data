package io.micronaut.transaction;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class OpLogger{

    private final List<String> logs = new ArrayList<>();

    public void add(String log) {
        logs.add(log);
    }

    public List<String> getLogs() {
        return logs;
    }
}
