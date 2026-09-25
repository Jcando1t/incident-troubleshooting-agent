package com.example.incident.agent;

import com.example.incident.domain.Incident;

public interface IncidentAgent<I, O> {
    String name();
    O execute(Incident incident, I input);
}
