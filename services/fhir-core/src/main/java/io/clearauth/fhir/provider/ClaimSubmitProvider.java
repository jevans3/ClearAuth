package io.clearauth.fhir.provider;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import io.clearauth.fhir.adjudication.AdjudicationService;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;

/**
 * Implements the Da Vinci PAS-style prior authorization intake:
 * POST [base]/Claim/$submit with a request Bundle containing the Claim
 * and its supporting resources. Returns a Bundle containing the ClaimResponse.
 */
@Component
public class ClaimSubmitProvider {

    private final AdjudicationService adjudicationService;

    public ClaimSubmitProvider(AdjudicationService adjudicationService) {
        this.adjudicationService = adjudicationService;
    }

    @Operation(name = "$submit", typeName = "Claim", idempotent = false)
    public Bundle submit(@OperationParam(name = "resource", min = 1, max = 1) Bundle requestBundle) {
        if (requestBundle == null || requestBundle.getEntry().isEmpty()) {
            throw new InvalidRequestException("Request bundle is missing or empty");
        }
        return adjudicationService.adjudicate(requestBundle);
    }
}
