package io.clearauth.fhir.interop;

import ca.uhn.fhir.context.FhirContext;
import io.clearauth.fhir.adjudication.PolicyCatalog;
import io.clearauth.fhir.adjudication.PolicyRule;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Questionnaire;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Da Vinci DTR (Documentation Templates and Rules) — lite.
 *
 * Generates a FHIR Questionnaire from the policy rule for a procedure code, so
 * the ordering provider collects exactly the documentation the policy needs.
 * The completed QuestionnaireResponse is included in the PAS submit bundle and
 * its answers are forwarded to the review copilot as clinical evidence.
 */
@RestController
public class DtrController {

    private final PolicyCatalog policyCatalog;
    private final FhirContext fhirContext;

    public DtrController(PolicyCatalog policyCatalog, FhirContext fhirContext) {
        this.policyCatalog = policyCatalog;
        this.fhirContext = fhirContext;
    }

    @GetMapping(value = "/api/dtr/questionnaire/{code}", produces = "application/fhir+json")
    public ResponseEntity<String> questionnaire(@PathVariable String code) {
        PolicyRule rule = policyCatalog.findByCode(code).orElse(null);

        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setId("clearauth-dtr-" + code);
        questionnaire.setName("ClearAuthDtr" + code);
        questionnaire.setTitle("Prior authorization documentation: "
                + (rule != null ? rule.description() : "procedure " + code));
        questionnaire.setStatus(Enumerations.PublicationStatus.ACTIVE);

        Questionnaire.QuestionnaireItemComponent dx = questionnaire.addItem();
        dx.setLinkId("diagnosis-basis").setType(Questionnaire.QuestionnaireItemType.STRING)
                .setText(rule != null && !rule.approvedDiagnosisPrefixes().isEmpty()
                        ? "Primary ICD-10 diagnosis supporting this request (policy "
                          + rule.policyId() + " covers prefixes " + rule.approvedDiagnosisPrefixes() + ")"
                        : "Primary ICD-10 diagnosis supporting this request");

        Questionnaire.QuestionnaireItemComponent history = questionnaire.addItem();
        history.setLinkId("clinical-justification").setType(Questionnaire.QuestionnaireItemType.TEXT)
                .setText("Clinical justification: symptom duration, conservative therapy "
                        + "tried and outcome, red-flag findings if any");

        Questionnaire.QuestionnaireItemComponent urgency = questionnaire.addItem();
        urgency.setLinkId("expedited").setType(Questionnaire.QuestionnaireItemType.BOOLEAN)
                .setText("Expedited review requested (member's health could be seriously "
                        + "jeopardized by the standard timeframe)");

        String json = fhirContext.newJsonParser().setPrettyPrint(true)
                .encodeResourceToString(questionnaire);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/fhir+json"))
                .body(json);
    }
}
