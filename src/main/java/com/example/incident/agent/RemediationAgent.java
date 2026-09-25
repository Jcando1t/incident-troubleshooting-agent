package com.example.incident.agent;

import com.example.incident.domain.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class RemediationAgent implements IncidentAgent<RcaResult, RemediationPlan> {
    @Override public String name() { return "remediation-agent"; }

    @Override
    public RemediationPlan execute(Incident incident, RcaResult rca) {
        var step = new RemediationStep("暂停继续发布并执行人工确认后的回滚",
                "# TODO: bind an allow-listed runbook", "# TODO: bind rollback runbook", RiskLevel.HIGH);
        return new RemediationPlan(List.of(step), true);
    }
}
