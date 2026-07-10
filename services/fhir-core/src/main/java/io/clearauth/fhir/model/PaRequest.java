package io.clearauth.fhir.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Decision-log row for one prior authorization request.
 * Feeds the SLA tracker and the CMS-mandated public PA metrics.
 */
@Entity
@Table(name = "pa_request")
public class PaRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String claimId;
    private String patientRef;
    private String serviceCode;
    private String priority;     // expedited | standard
    private String status;       // APPROVED | PENDED | DENIED (human review only)
    private String disposition;
    private Instant receivedAt;
    private Instant dueAt;       // SLA deadline: 72h expedited / 7d standard
    private Instant decidedAt;

    public Long getId() { return id; }
    public String getClaimId() { return claimId; }
    public void setClaimId(String claimId) { this.claimId = claimId; }
    public String getPatientRef() { return patientRef; }
    public void setPatientRef(String patientRef) { this.patientRef = patientRef; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDisposition() { return disposition; }
    public void setDisposition(String disposition) { this.disposition = disposition; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
