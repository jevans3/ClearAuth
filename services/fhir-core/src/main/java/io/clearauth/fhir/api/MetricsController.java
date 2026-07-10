package io.clearauth.fhir.api;

import io.clearauth.fhir.model.PaRequest;
import io.clearauth.fhir.model.PaRequestRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CMS-0057-F requires payers to publicly report prior authorization metrics
 * (approval/denial rates, average decision time). This endpoint is the
 * machine-readable version of that report.
 */
@RestController
public class MetricsController {

    private final PaRequestRepository repository;

    public MetricsController(PaRequestRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/metrics")
    public Map<String, Object> metrics() {
        List<PaRequest> all = repository.findAll();
        long total = all.size();
        long approved = repository.countByStatus("APPROVED");
        long pended = repository.countByStatus("PENDED");
        long denied = repository.countByStatus("DENIED");

        double avgDecisionSeconds = all.stream()
                .filter(r -> r.getDecidedAt() != null)
                .mapToLong(r -> Duration.between(r.getReceivedAt(), r.getDecidedAt()).toSeconds())
                .average()
                .orElse(0);

        long slaBreaches = all.stream()
                .filter(r -> r.getDecidedAt() == null && r.getDueAt().isBefore(Instant.now()))
                .count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalRequests", total);
        out.put("approved", approved);
        out.put("pended", pended);
        out.put("denied", denied);
        out.put("approvalRate", total == 0 ? 0 : Math.round(approved * 1000.0 / total) / 10.0);
        out.put("avgDecisionSeconds", avgDecisionSeconds);
        out.put("expeditedRequests", repository.countByPriority("expedited"));
        out.put("standardRequests", repository.countByPriority("standard"));
        out.put("openPastSlaDeadline", slaBreaches);
        return out;
    }
}
