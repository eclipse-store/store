package org.eclipse.store.integrations.spring.boot.types.configuration;

/*-
 * #%L
 * EclipseStore Integrations SpringBoot
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

/**
 * Configuration of the per-chunk data-integrity checksum feature.
 *
 * <p>The simple tier ({@link #algorithm}, {@link #profile}, {@link #seed}) is sufficient for almost all
 * users; the remaining per-axis fields are an expert tier that overrides individual axes of the profile.
 * The boolean overrides are {@link Boolean} (not primitive) so that an unset value stays distinct from an
 * explicit {@code false} and the profile's value is used when absent.</p>
 */
public class ChunkChecksum
{
    /**
     * Primary algorithm: {@code none}, {@code crc32c} or {@code sha256-chained}.
     * Default {@code sha256-chained}.
     */
    private String algorithm;

    /**
     * Base policy profile: {@code default}, {@code off}, {@code observe}, {@code strict} or
     * {@code strict-tolerate-legacy}. Default {@code default}.
     */
    private String profile;

    /**
     * Initial chain seed (hex, 64 chars = 32 bytes) for {@code sha256-chained}; unused otherwise.
     */
    private String seed;

    /**
     * Expert override: whether to emit checksum records on write. Defaults to the profile's value.
     */
    private Boolean emit;

    /**
     * Expert override: whether to recompute and check on load. Defaults to the profile's value.
     */
    private Boolean verify;

    /**
     * Expert override: reaction ({@code ignore} / {@code log} / {@code fail}) to a checksum mismatch.
     */
    private String onChecksumMismatch;

    /**
     * Expert override: reaction ({@code ignore} / {@code log} / {@code fail}) to a chunk-boundary mismatch.
     */
    private String onBoundaryMismatch;

    /**
     * Expert override: reaction ({@code ignore} / {@code log} / {@code fail}) to an unknown record kind.
     */
    private String onUnknownKind;

    /**
     * Expert override: reaction ({@code ignore} / {@code log} / {@code fail}) to a missing file header.
     */
    private String onMissingHeader;

    /**
     * Expert override: reaction ({@code ignore} / {@code log} / {@code fail}) to uncovered data.
     */
    private String onUncoveredData;

    /**
     * Expert override: whether missing/uncovered files are raised as anomalies. Defaults to the profile's value.
     */
    private Boolean requireCoverage;

    /**
     * Expert override: whether enabling emit forces an immediately-covered head file. Defaults to the profile's value.
     */
    private Boolean continuousCoverage;

    public String getAlgorithm()
    {
        return this.algorithm;
    }

    public void setAlgorithm(final String algorithm)
    {
        this.algorithm = algorithm;
    }

    public String getProfile()
    {
        return this.profile;
    }

    public void setProfile(final String profile)
    {
        this.profile = profile;
    }

    public String getSeed()
    {
        return this.seed;
    }

    public void setSeed(final String seed)
    {
        this.seed = seed;
    }

    public Boolean getEmit()
    {
        return this.emit;
    }

    public void setEmit(final Boolean emit)
    {
        this.emit = emit;
    }

    public Boolean getVerify()
    {
        return this.verify;
    }

    public void setVerify(final Boolean verify)
    {
        this.verify = verify;
    }

    public String getOnChecksumMismatch()
    {
        return this.onChecksumMismatch;
    }

    public void setOnChecksumMismatch(final String onChecksumMismatch)
    {
        this.onChecksumMismatch = onChecksumMismatch;
    }

    public String getOnBoundaryMismatch()
    {
        return this.onBoundaryMismatch;
    }

    public void setOnBoundaryMismatch(final String onBoundaryMismatch)
    {
        this.onBoundaryMismatch = onBoundaryMismatch;
    }

    public String getOnUnknownKind()
    {
        return this.onUnknownKind;
    }

    public void setOnUnknownKind(final String onUnknownKind)
    {
        this.onUnknownKind = onUnknownKind;
    }

    public String getOnMissingHeader()
    {
        return this.onMissingHeader;
    }

    public void setOnMissingHeader(final String onMissingHeader)
    {
        this.onMissingHeader = onMissingHeader;
    }

    public String getOnUncoveredData()
    {
        return this.onUncoveredData;
    }

    public void setOnUncoveredData(final String onUncoveredData)
    {
        this.onUncoveredData = onUncoveredData;
    }

    public Boolean getRequireCoverage()
    {
        return this.requireCoverage;
    }

    public void setRequireCoverage(final Boolean requireCoverage)
    {
        this.requireCoverage = requireCoverage;
    }

    public Boolean getContinuousCoverage()
    {
        return this.continuousCoverage;
    }

    public void setContinuousCoverage(final Boolean continuousCoverage)
    {
        this.continuousCoverage = continuousCoverage;
    }
}
