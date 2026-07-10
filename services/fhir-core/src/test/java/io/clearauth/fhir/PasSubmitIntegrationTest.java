package io.clearauth.fhir;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PasSubmitIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private ResponseEntity<String> submit(String classpathBundle) throws Exception {
        String body = new String(
                new ClassPathResource(classpathBundle).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/fhir+json"));
        return rest.postForEntity("/fhir/Claim/$submit", new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void mriWithQualifyingDiagnosisIsApproved() throws Exception {
        ResponseEntity<String> response = submit("bundles/pa-request-approve.json");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("ClaimResponse");
        assertThat(response.getBody()).contains("\"disposition\":\"Approved\"");
        assertThat(response.getBody()).contains("preAuthRef");
        assertThat(response.getBody()).contains("MP-IMG-001");
    }

    @Test
    void unclassifiedDrugIsPendedNeverDenied() throws Exception {
        ResponseEntity<String> response = submit("bundles/pa-request-pend.json");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Pended for clinical review");
        assertThat(response.getBody()).doesNotContain("\"disposition\":\"Denied\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void metricsEndpointReportsDecisions() throws Exception {
        submit("bundles/pa-request-approve.json");

        Map<String, Object> metrics = rest.getForObject("/api/metrics", Map.class);
        assertThat(((Number) metrics.get("totalRequests")).longValue()).isGreaterThan(0);
        assertThat(metrics).containsKeys("approvalRate", "openPastSlaDeadline", "avgDecisionSeconds");
    }
}
