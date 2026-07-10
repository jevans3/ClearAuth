package io.clearauth.fhir.adjudication;

import java.util.List;

/**
 * A single medical-necessity policy rule keyed by procedure code (CPT/HCPCS).
 *
 * action:
 *   approve — auto-approve
 *   review  — approve when a claim diagnosis matches an approved ICD-10 prefix, else pend
 *   pend    — always queue for human clinical review (ClearAuth never auto-denies)
 */
public record PolicyRule(
        String code,
        String description,
        String action,
        List<String> approvedDiagnosisPrefixes,
        String policyId) {
}
