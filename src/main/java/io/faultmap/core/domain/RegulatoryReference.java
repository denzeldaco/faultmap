package io.faultmap.core.domain;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A reference to a specific article or section of a regulatory framework.
 *
 * This is the core of Faultmap's moat — every Finding is tagged with the
 * exact regulatory obligations it violates. This is what the compliance
 * report shows CBN or NDPC auditors.
 *
 * Example:
 *   framework  = "NDPC 2023"
 *   article    = "Article 26(1)"
 *   obligation = "Data controllers must implement appropriate technical
 *                 measures to prevent unauthorised access to personal data."
 *   docUrl     = "https://ndpc.gov.ng/..."
 */
@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulatoryReference {

    /** Short name of the regulatory framework. */
    private String framework;

    /** Specific article, section, or rule number. */
    private String article;

    /** Plain-English description of the obligation being violated. */
    private String obligation;

    /** Link to the official regulatory document. */
    private String docUrl;

    @Override
    public String toString() {
        return framework + " · " + article;
    }
}