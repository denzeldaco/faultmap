package io.faultmap.core.engine;

import io.faultmap.core.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FindingRepository extends JpaRepository<Finding, String> {

    List<Finding> findByScanResultId(String scanResultId);

    List<Finding> findByScanResultIdAndSeverity(String scanResultId, Severity severity);

    List<Finding> findByScanResultIdAndStatus(String scanResultId, FindingStatus status);

    @Query("""
        SELECT f FROM Finding f
        WHERE f.scanResultId IN (
            SELECT sr.scanResultId FROM ScanResult sr
            WHERE sr.organisationId = :organisationId
        )
        AND f.status = 'OPEN'
        ORDER BY
            CASE f.severity
                WHEN 'CRITICAL' THEN 0
                WHEN 'HIGH'     THEN 1
                WHEN 'MEDIUM'   THEN 2
                WHEN 'LOW'      THEN 3
                ELSE 4
            END
    """)
    List<Finding> findOpenFindingsByOrganisation(String organisationId);

    long countByScanResultIdAndSeverity(String scanResultId, Severity severity);
}