package io.clearauth.fhir.interop;

import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;

/**
 * Simplified X12 278 (005010X217) mapper.
 *
 * Payer cores and clearinghouses still speak X12; CMS-0057-F FHIR APIs sit in
 * front of them. This converter produces structurally-valid 278 request /
 * response interchanges from the FHIR resources so ClearAuth can bridge both
 * worlds. Scope: the segments a downstream UM system needs (ISA/GS/ST, BHT,
 * HL hierarchy, UM, HI, HCR) — not a certified translator.
 */
@Component
public class X12Converter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");

    public String toRequest278(Claim claim) {
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DATE);
        String time = now.format(TIME);
        String claimId = claim.getIdElement().getIdPart();

        StringJoiner x12 = new StringJoiner("~\n", "", "~\n");
        x12.add("ISA*00*          *00*          *ZZ*SUBMITTER      *ZZ*CLEARAUTH      *"
                + date.substring(2) + "*" + time + "*^*00501*000000001*0*T*:");
        x12.add("GS*HI*SUBMITTER*CLEARAUTH*" + date + "*" + time + "*1*X*005010X217");
        x12.add("ST*278*0001*005010X217");
        x12.add("BHT*0007*13*" + claimId + "*" + date + "*" + time);
        // HL 1: Utilization Management Organization (the payer)
        x12.add("HL*1**20*1");
        x12.add("NM1*X3*2*CLEARAUTH DEMO HEALTH PLAN*****PI*CLEARAUTH");
        // HL 2: Requester (ordering provider)
        x12.add("HL*2*1*21*1");
        x12.add("NM1*1P*2*ORDERING PROVIDER*****XX*0000000000");
        // HL 3: Subscriber
        x12.add("HL*3*2*22*1");
        x12.add("NM1*IL*1*MEMBER*****MI*" + memberId(claim));
        // HL 4: Service level
        x12.add("HL*4*3*SS*0");
        x12.add("TRN*1*" + claimId + "*9CLEARAUTH");
        x12.add("UM*HS*I*3*" + (isExpedited(claim) ? "03" : "02") + "*****Y");
        String dxSegment = diagnosisSegment(claim);
        if (!dxSegment.isEmpty()) {
            x12.add(dxSegment);
        }
        x12.add("SV1*HC:" + primaryProcedureCode(claim) + "*0*UN*1***1");
        x12.add("SE*" + 15 + "*0001");
        x12.add("GE*1*1");
        x12.add("IEA*1*000000001");
        return x12.toString();
    }

    public String toResponse278(ClaimResponse response) {
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DATE);
        String time = now.format(TIME);
        boolean approved = response.getDisposition() != null
                && response.getDisposition().startsWith("Approved");
        // HCR01: A1 = certified in total, A4 = pended
        String action = approved ? "A1" : "A4";
        String authRef = approved && response.getPreAuthRef() != null
                ? response.getPreAuthRef() : "PENDING";

        StringJoiner x12 = new StringJoiner("~\n", "", "~\n");
        x12.add("ISA*00*          *00*          *ZZ*CLEARAUTH      *ZZ*SUBMITTER      *"
                + date.substring(2) + "*" + time + "*^*00501*000000002*0*T*:");
        x12.add("GS*HI*CLEARAUTH*SUBMITTER*" + date + "*" + time + "*2*X*005010X217");
        x12.add("ST*278*0002*005010X217");
        x12.add("BHT*0007*11*" + response.getIdElement().getIdPart() + "*" + date + "*" + time);
        x12.add("HL*1**20*1");
        x12.add("NM1*X3*2*CLEARAUTH DEMO HEALTH PLAN*****PI*CLEARAUTH");
        x12.add("HL*2*1*21*1");
        x12.add("NM1*1P*2*ORDERING PROVIDER*****XX*0000000000");
        x12.add("HL*3*2*22*0");
        x12.add("HCR*" + action + "*" + authRef);
        x12.add("SE*10*0002");
        x12.add("GE*1*2");
        x12.add("IEA*1*000000002");
        return x12.toString();
    }

    private String memberId(Claim claim) {
        String reference = claim.getPatient().getReference();
        if (reference == null) {
            return "UNKNOWN";
        }
        int slash = Math.max(reference.lastIndexOf('/'), reference.lastIndexOf(':'));
        return reference.substring(slash + 1).toUpperCase();
    }

    private boolean isExpedited(Claim claim) {
        return "stat".equalsIgnoreCase(claim.getPriority().getCodingFirstRep().getCode());
    }

    private String primaryProcedureCode(Claim claim) {
        return claim.getItemFirstRep().getProductOrService().getCodingFirstRep().getCode();
    }

    private String diagnosisSegment(Claim claim) {
        StringJoiner hi = new StringJoiner("*", "HI*", "");
        boolean any = false;
        for (Claim.DiagnosisComponent dx : claim.getDiagnosis()) {
            if (dx.getDiagnosis() instanceof CodeableConcept concept) {
                for (Coding coding : concept.getCoding()) {
                    if (coding.getCode() != null) {
                        hi.add("ABF:" + coding.getCode().replace(".", ""));
                        any = true;
                    }
                }
            }
        }
        return any ? hi.toString() : "";
    }
}
