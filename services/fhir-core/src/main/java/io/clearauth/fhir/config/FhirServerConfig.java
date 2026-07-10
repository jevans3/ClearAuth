package io.clearauth.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import io.clearauth.fhir.provider.ClaimSubmitProvider;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mounts a HAPI FHIR RestfulServer at /fhir/* inside the Spring Boot app.
 */
@Configuration
public class FhirServerConfig {

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public ServletRegistrationBean<RestfulServer> fhirServlet(FhirContext fhirContext,
                                                              ClaimSubmitProvider claimSubmitProvider) {
        RestfulServer server = new RestfulServer(fhirContext);
        server.registerProvider(claimSubmitProvider);
        server.setDefaultResponseEncoding(EncodingEnum.JSON);

        ServletRegistrationBean<RestfulServer> registration =
                new ServletRegistrationBean<>(server, "/fhir/*");
        registration.setName("fhirServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
