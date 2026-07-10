package io.clearauth.fhir.adjudication;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the open policy catalog from classpath:criteria/policies.yaml.
 * Every decision cites the policyId of the rule that produced it, giving
 * providers the "specific reason" CMS-0057-F requires.
 */
@Component
public class PolicyCatalog {

    private final Map<String, PolicyRule> rulesByCode = new HashMap<>();
    private String defaultAction = "pend";

    @PostConstruct
    @SuppressWarnings("unchecked")
    void load() throws Exception {
        try (InputStream in = new ClassPathResource("criteria/policies.yaml").getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            defaultAction = (String) root.getOrDefault("defaultAction", "pend");
            List<Map<String, Object>> policies = (List<Map<String, Object>>) root.get("policies");
            for (Map<String, Object> p : policies) {
                PolicyRule rule = new PolicyRule(
                        String.valueOf(p.get("code")),
                        (String) p.getOrDefault("description", ""),
                        (String) p.getOrDefault("action", "pend"),
                        (List<String>) p.getOrDefault("approvedDiagnosisPrefixes", List.of()),
                        (String) p.getOrDefault("policyId", "MP-DEFAULT"));
                rulesByCode.put(rule.code(), rule);
            }
        }
    }

    public Optional<PolicyRule> findByCode(String procedureCode) {
        return Optional.ofNullable(rulesByCode.get(procedureCode));
    }

    public String defaultAction() {
        return defaultAction;
    }

    public int size() {
        return rulesByCode.size();
    }
}
