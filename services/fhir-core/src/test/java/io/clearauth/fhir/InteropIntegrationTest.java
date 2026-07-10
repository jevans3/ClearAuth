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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InteropIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String readBundle(String path) throws Exception {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    @Test
    void cdsDiscoveryAdvertisesCrdService() {
        String body = rest.getForObject("/cds-services", String.class);
        assertThat(body).contains("clearauth-crd").contains("order-select");
    }

    @Test
    void crdFlagsPriorAuthRequiredForMri() {
        String hookRequest = """
                {"hook":"order-select","context":{"draftOrders":{"resourceType":"Bundle","entry":[
                  {"resource":{"resourceType":"ServiceRequest","code":{"coding":[
                    {"system":"http://www.ama-assn.org/go/cpt","code":"70551","display":"MRI brain"}]}}}]}}}
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = rest.postForObject("/cds-services/clearauth-crd",
                new HttpEntity<>(hookRequest, headers), String.class);
        assertThat(body).contains("Prior authorization required")
                .contains("MP-IMG-001")
                .contains("/api/dtr/questionnaire/70551");
    }

    @Test
    void crdReportsNoAuthNeededForAutoApprovedService() {
        String hookRequest = """
                {"hook":"order-select","context":{"draftOrders":{"resourceType":"Bundle","entry":[
                  {"resource":{"resourceType":"ServiceRequest","code":{"coding":[
                    {"code":"97110","display":"Physical therapy"}]}}}]}}}
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = rest.postForObject("/cds-services/clearauth-crd",
                new HttpEntity<>(hookRequest, headers), String.class);
        assertThat(body).contains("No prior authorization required");
    }

    @Test
    void dtrGeneratesQuestionnaireFromPolicy() {
        String body = rest.getForObject("/api/dtr/questionnaire/70551", String.class);
        assertThat(body).contains("\"resourceType\": \"Questionnaire\"")
                .contains("MP-IMG-001")
                .contains("clinical-justification")
                .contains("expedited");
    }

    @Test
    void x12RequestMappingProducesStructural278() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/fhir+json"));
        ResponseEntity<String> response = rest.postForEntity("/api/x12/278/request",
                new HttpEntity<>(readBundle("bundles/pa-request-approve.json"), headers), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String x12 = response.getBody();
        assertThat(x12).contains("ST*278*0001*005010X217")
                .contains("UM*HS*I*3*02")          // standard priority
                .contains("HI*ABF:G43909")          // ICD-10 without dot
                .contains("SV1*HC:70551");
    }

    @Test
    void fhirMetadataCapabilityStatementIsServed() {
        String body = rest.getForObject("/fhir/metadata", String.class);
        assertThat(body).contains("CapabilityStatement");
    }
}
