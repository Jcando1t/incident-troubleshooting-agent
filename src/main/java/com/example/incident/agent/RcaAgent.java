package com.example.incident.agent;

import com.example.incident.domain.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class RcaAgent implements IncidentAgent<EvidenceBundle, RcaResult> {
    @Override public String name() { return "rca-agent"; }

    @Override
    public RcaResult execute(Incident incident, EvidenceBundle evidence) {
        var refs = evidence.items().stream().map(Evidence::reference).toList();
        return new RcaResult("待由模型结合观测证据确认的变更或依赖异常",
                List.of("需要核对最近发布", "需要核对依赖健康度"), refs,
                evidence.complete() ? 0.72 : 0.41);
    }
}
