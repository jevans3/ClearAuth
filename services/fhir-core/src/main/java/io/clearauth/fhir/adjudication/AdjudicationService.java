package io.clearauth.fhir.adjudication;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import io.clearauth.fhir.model.PaRequest;
import io.clearauth.fhir.model.PaRequestRepository;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic, auditable prior-auth adjudication.
 *
 * Design stance: the engine APPROVES or PENDS. It never auto-denies —
 * denial requires human clinical review (recorded in Phase 2's review queue).
 * Every decision cites the policy rule that produced it.
 */
@Service
public class AdjudicationService {

    public static final String PAS_CLAIMRESPONSE_PROFILE =
            "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claimresponse";

    private final PolicyCatalog policyCatalog;
    private final PaRequestRepository repository;
    private final ReviewCopilotClient reviewCopilot;

    public AdjudicationService(PolicyCatalog policyCatalog, PaRequestRepository repository,
                               ReviewCopilotClient reviewCopilot) {
        this.policyCatalog = policyCatalog;
        this.repository = repository;
        this.reviewCopilot = reviewCopilot;
    }

    public Bundle adjudicate(Bundle requestBundle) {
        Instant receivedAt = Instant.now();
        Claim claim = extractClaim(requestBundle);

        boolean expedited = isExpedited(claim);
        Instant dueAt = expedited
                ? receivedAt.plus(72, ChronoUnit.HOURS)
                : receivedAt.plus(7, ChronoUnit.DAYS);

        List<String> notes = new ArrayList<>();
        boolean allApproved = true;

        for (Claim.ItemComponent item : claim.getItem()) {
            String code = item.getProductOrService().getCodingFirstRep().getCode();
            ItemDecision decision = decideItem(code, claim);
            notes.add(decision.note());
            if (!decision.approved()) {
                allApproved = false;
            }
        }
        if (claim.getItem().isEmpty()) {
            throw new InvalidRequestException("Claim contains no items to adjudicate");
        }

        if (!allApproved && reviewCopilot.isEnabled()) {
            reviewCopilot.recommend(
                            claim.getItemFirstRep().getProductOrService().getCodingFirstRep().getCode(),
                            extractDiagnosisCodes(claim),
                            "")
                    .ifPresent(notes::add);
        }

        String disposition = allApproved ? "Approved" : "Pended for clinical review";
        String preAuthRef = allApproved ? "CA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() : null;

        persist(claim, expedited, receivedAt, dueAt, allApproved, disposition);

        ClaimResponse response = buildClaimResponse(claim, allApproved, disposition, preAuthRef, notes);

        Bundle out = new Bundle();
        out.setType(Bundle.BundleType.COLLECTION);
        out.addEntry()
                .setFullUrl("urn:uuid:" + UUID.randomUUID())
                .setResource(response);
        return out;
    }

    private ItemDecision decideItem(String procedureCode, Claim claim) {
        PolicyRule rule = policyCatalog.findByCode(procedureCode).orElse(null);
        if (rule == null) {
            return new ItemDecision(false,
                    "Code " + procedureCode + ": no policy on file — pended for clinical review (policy MP-DEFAULT)");
        }
        return switch (rule.action()) {
            case "approve" -> new ItemDecision(true,
                    "Code " + procedureCode + ": auto-approved per policy " + rule.policyId());
            case "review" -> {
                boolean match = claimHasApprovedDiagnosis(claim, rule.approvedDiagnosisPrefixes());
                yield match
                        ? new ItemDecision(true, "Code " + procedureCode
                            + ": approved — supporting diagnosis meets policy " + rule.policyId())
                        : new ItemDecision(false, "Code " + procedureCode
                            + ": pended — no qualifying diagnosis under policy " + rule.policyId()
                            + " (requires ICD-10 prefix in " + rule.approvedDiagnosisPrefixes() + ")");
            }
            default -> new ItemDecision(false,
                    "Code " + procedureCode + ": pended for clinical review per policy " + rule.policyId());
        };
    }

    private List<String> extractDiagnosisCodes(Claim claim) {
        List<String> codes = new ArrayList<>();
        for (Claim.DiagnosisComponent dx : claim.getDiagnosis()) {
            if (dx.getDiagnosis() instanceof CodeableConcept concept) {
                for (Coding coding : concept.getCoding()) {
                    if (coding.getCode() != null) {
                        codes.add(coding.getCode());
                    }
                }
            }
        }
        return codes;
    }

    private boolean claimHasApprovedDiagnosis(Claim claim, List<String> prefixes) {
        for (Claim.DiagnosisComponent dx : claim.getDiagnosis()) {
            if (!(dx.getDiagnosis() instanceof CodeableConcept concept)) {
                continue;
            }
            for (Coding coding : concept.getCoding()) {
                String code = coding.getCode();
                if (code != null && prefixes.stream().anyMatch(code::startsWith)) {
                    return true;
                }
            }
        }
        return false;
    }

    private ClaimResponse buildClaimResponse(Claim claim, boolean approved, String disposition,
                                             String preAuthRef, List<String> notes) {
        ClaimResponse response = new ClaimResponse();
        response.setId(UUID.randomUUID().toString());
        response.getMeta().addProfile(PAS_CLAIMRESPONSE_PROFILE);
        response.setStatus(ClaimResponse.ClaimResponseStatus.ACTIVE);
        response.setType(claim.getType());
        response.setUse(ClaimResponse.Use.PREAUTHORIZATION);
        response.setPatient(claim.getPatient());
        response.setCreated(new Date());
        response.setInsurer(new Reference().setDisplay("ClearAuth Demo Health Plan"));
        response.setRequest(new Reference("Claim/" + claim.getIdElement().getIdPart()));
        response.setOutcome(ClaimResponse.RemittanceOutcome.COMPLETE);
        response.setDisposition(disposition);

        if (approved && preAuthRef != null) {
            response.setPreAuthRef(preAuthRef);
            Date start = new Date();
            Date end = Date.from(Instant.now().plus(90, ChronoUnit.DAYS));
            response.setPreAuthPeriod(new Period().setStart(start).setEnd(end));
        }
        int noteNumber = 1;
        for (String note : notes) {
            response.addProcessNote()
                    .setNumber(noteNumber++)
                    .setText(note);
        }
        return response;
    }

    private void persist(Claim claim, boolean expedited, Instant receivedAt, Instant dueAt,
                         boolean approved, String disposition) {
        PaRequest record = new PaRequest();
        record.setClaimId(claim.getIdElement().getIdPart());
        record.setPatientRef(claim.getPatient().getReference());
        record.setServiceCode(claim.getItemFirstRep().getProductOrService().getCodingFirstRep().getCode());
        record.setPriority(expedited ? "expedited" : "standard");
        record.setStatus(approved ? "APPROVED" : "PENDED");
        record.setDisposition(disposition);
        record.setReceivedAt(receivedAt);
        record.setDueAt(dueAt);
        record.setDecidedAt(approved ? Instant.now() : null);
        repository.save(record);
    }

    private boolean isExpedited(Claim claim) {
        String priority = claim.getPriority().getCodingFirstRep().getCode();
        return "stat".equalsIgnoreCase(priority);
    }

    private Claim extractClaim(Bundle bundle) {
        return bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(Claim.class::isInstance)
                .map(Claim.class::cast)
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("Request bundle must contain a Claim resource"));
    }

    private record ItemDecision(boolean approved, String note) {
    }
}
