package io.clearauth.fhir.interop;

import com.fasterxml.jackson.databind.JsonNode;
import io.clearauth.fhir.adjudication.PolicyCatalog;
import io.clearauth.fhir.adjudication.PolicyRule;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Da Vinci CRD (Coverage Requirements Discovery) via CDS Hooks.
 *
 * EHRs call the discovery document at ordering time; for each draft order we
 * answer whether prior authorization is required and link the DTR
 * questionnaire that collects the documentation the policy needs.
 * https://cds-hooks.hl7.org / https://hl7.org/fhir/us/davinci-crd/
 */
@RestController
public class CrdController {

    public static final String SERVICE_ID = "clearauth-crd";

    private final PolicyCatalog policyCatalog;

    public CrdController(PolicyCatalog policyCatalog) {
        this.policyCatalog = policyCatalog;
    }

    @GetMapping("/cds-services")
    public Map<String, Object> discovery() {
        return Map.of("services", List.of(Map.of(
                "hook", "order-select",
                "id", SERVICE_ID,
                "title", "ClearAuth Coverage Requirements Discovery",
                "description", "Tells the ordering provider whether prior authorization "
                        + "is required and links the documentation questionnaire.",
                "prefetch", Map.of()
        )));
    }

    @PostMapping("/cds-services/" + SERVICE_ID)
    public Map<String, Object> orderSelect(@RequestBody JsonNode request) {
        List<Map<String, Object>> cards = new ArrayList<>();
        JsonNode entries = request.path("context").path("draftOrders").path("entry");
        for (JsonNode entry : entries) {
            JsonNode resource = entry.path("resource");
            if (!"ServiceRequest".equals(resource.path("resourceType").asText())) {
                continue;
            }
            JsonNode coding = resource.path("code").path("coding").path(0);
            String code = coding.path("code").asText("");
            String display = coding.path("display").asText(code);
            cards.add(cardFor(code, display));
        }
        if (cards.isEmpty()) {
            cards.add(Map.of(
                    "summary", "No draft orders found in context",
                    "indicator", "info",
                    "source", source()));
        }
        return Map.of("cards", cards);
    }

    private Map<String, Object> cardFor(String code, String display) {
        Optional<PolicyRule> rule = policyCatalog.findByCode(code);
        if (rule.isPresent() && "approve".equals(rule.get().action())) {
            return Map.of(
                    "summary", "No prior authorization required: " + display,
                    "detail", "Policy " + rule.get().policyId() + " auto-approves " + display + ".",
                    "indicator", "info",
                    "source", source());
        }
        String policyId = rule.map(PolicyRule::policyId).orElse("MP-DEFAULT");
        return Map.of(
                "summary", "Prior authorization required: " + display,
                "detail", "Policy " + policyId + " requires documentation before this order "
                        + "can be authorized. Complete the linked questionnaire (DTR) and "
                        + "submit via Claim/$submit.",
                "indicator", "warning",
                "source", source(),
                "links", List.of(Map.of(
                        "label", "Documentation questionnaire (DTR)",
                        "url", "/api/dtr/questionnaire/" + code,
                        "type", "absolute")));
    }

    private Map<String, Object> source() {
        return Map.of("label", "ClearAuth", "url", "https://github.com/AravindB98/ClearAuth");
    }
}
