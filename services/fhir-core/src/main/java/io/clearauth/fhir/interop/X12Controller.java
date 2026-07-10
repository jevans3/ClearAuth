package io.clearauth.fhir.interop;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * FHIR -> X12 278 bridge endpoints for legacy UM systems and clearinghouses.
 */
@RestController
public class X12Controller {

    private final X12Converter converter;
    private final FhirContext fhirContext;

    public X12Controller(X12Converter converter, FhirContext fhirContext) {
        this.converter = converter;
        this.fhirContext = fhirContext;
    }

    @PostMapping(value = "/api/x12/278/request", consumes = {"application/fhir+json", "application/json"},
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> toRequest(@RequestBody String bundleJson) {
        Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, bundleJson);
        Claim claim = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(Claim.class::isInstance)
                .map(Claim.class::cast)
                .findFirst()
                .orElse(null);
        if (claim == null) {
            return ResponseEntity.badRequest().body("Bundle must contain a Claim resource");
        }
        return ResponseEntity.ok(converter.toRequest278(claim));
    }

    @PostMapping(value = "/api/x12/278/response", consumes = {"application/fhir+json", "application/json"},
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> toResponse(@RequestBody String bundleJson) {
        Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, bundleJson);
        ClaimResponse claimResponse = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(ClaimResponse.class::isInstance)
                .map(ClaimResponse.class::cast)
                .findFirst()
                .orElse(null);
        if (claimResponse == null) {
            return ResponseEntity.badRequest().body("Bundle must contain a ClaimResponse resource");
        }
        return ResponseEntity.ok(converter.toResponse278(claimResponse));
    }
}
