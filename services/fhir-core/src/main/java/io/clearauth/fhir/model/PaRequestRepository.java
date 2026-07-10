package io.clearauth.fhir.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaRequestRepository extends JpaRepository<PaRequest, Long> {

    long countByStatus(String status);

    long countByPriority(String priority);

    List<PaRequest> findByStatus(String status);
}
