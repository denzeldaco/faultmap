package io.faultmap.core.engine;

import io.faultmap.core.domain.ScanResult;
import io.faultmap.core.domain.ScanStatus;
import io.faultmap.core.domain.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScanResultRepository extends JpaRepository<ScanResult, String> {

    List<ScanResult> findByOrganisationIdOrderByStartedAtDesc(String organisationId);

    List<ScanResult> findByOrganisationIdAndModuleOrderByStartedAtDesc(
            String organisationId, Module module);

    Optional<ScanResult> findTopByOrganisationIdAndModuleOrderByStartedAtDesc(
            String organisationId, Module module);

    List<ScanResult> findByStatus(ScanStatus status);
}