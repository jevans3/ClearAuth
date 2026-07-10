package io.clearauth.fhir.adjudication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Optional bridge to the review-copilot service. When configured
 * (clearauth.review-copilot.url), pended requests are sent for an
 * evidence-cited recommendation that is attached to the ClaimResponse
 * as a process note for the human reviewer.
 *
 * Failures are swallowed: the copilot is advisory, never in the
 * critical path of a prior-auth decision.
 */
@Component
public class ReviewCopilotClient {

    private static final Logger log = LoggerFactory.getLogger(ReviewCopilotClient.class);

    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public ReviewCopilotClient(@Value("${clearauth.review-copilot.url:}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    public Optional<String> recommend(String procedureCode, List<String> diagnosisCodes,
                                      String clinicalNote) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("procedure_code", procedureCode);
            payload.putPOJO("diagnosis_codes", diagnosisCodes);
            payload.put("clinical_note", clinicalNote == null ? "" : clinicalNote);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/review"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Review copilot returned status {}", response.statusCode());
                return Optional.empty();
            }
            JsonNode body = mapper.readTree(response.body());
            String recommendation = body.path("recommendation").asText("");
            String rationale = body.path("rationale").asText("");
            if (recommendation.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of("Review copilot recommendation: " + recommendation
                    + " — " + rationale);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Review copilot unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
