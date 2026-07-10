package io.clearauth.fhir.api;

import io.clearauth.fhir.model.PaRequest;
import io.clearauth.fhir.model.PaRequestRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Work-queue feed for the payer ops dashboard.
 */
@RestController
public class RequestsController {

    public record PaRequestView(Long id, String claimId, String serviceCode, String priority,
                                String status, String disposition, Instant receivedAt,
                                Instant dueAt, Instant decidedAt, boolean overdue) {
        static PaRequestView from(PaRequest r) {
            boolean overdue = r.getDecidedAt() == null && r.getDueAt() != null
                    && r.getDueAt().isBefore(Instant.now());
            return new PaRequestView(r.getId(), r.getClaimId(), r.getServiceCode(),
                    r.getPriority(), r.getStatus(), r.getDisposition(), r.getReceivedAt(),
                    r.getDueAt(), r.getDecidedAt(), overdue);
        }
    }

    private final PaRequestRepository repository;

    public RequestsController(PaRequestRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/requests")
    public List<PaRequestView> list(@RequestParam(defaultValue = "200") int limit) {
        return repository
                .findAll(PageRequest.of(0, Math.min(limit, 1000), Sort.by(Sort.Direction.DESC, "id")))
                .map(PaRequestView::from)
                .getContent();
    }
}
