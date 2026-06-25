package test.eclipse.store.library.types;

/*-
 * #%L
 * EclipseStore Integration Tests
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.junit.jupiter.api.Assertions;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class URIData implements BinaryHandlerTestData {

    URI uri;

    // ===== proposed edge-cases (review & cherry-pick) =====
    // URI has multiple parsing paths (hierarchical vs opaque, absolute vs relative). The handler is
    // expected to round-trip the raw form — anything reconstructible via new URI(toString()) should
    // survive. The probes below exercise structurally distinct shapes that exercise different code
    // paths in URI's internal parser.
    private URI opaqueUri;
    private URI urnUri;
    private URI relativeUri;
    private URI ipv6Uri;
    private URI encodedUri;
    private URI fragmentOnlyUri;
    private URI userInfoUri;

    @Override
    public URIData fillSampleData() {
        try {
            uri = new URL("http://example.com/pages/").toURI();

            // ===== proposed edge-cases =====
            opaqueUri = new URI("mailto:user@example.com");
            urnUri = new URI("urn:isbn:0451450523");
            relativeUri = new URI("../path/seg?q=1&r=2");
            ipv6Uri = new URI("http://[2001:db8::1]:8080/");
            encodedUri = new URI("http://example.com/p%20ath?q=a%26b#f%2Fg");
            fragmentOnlyUri = new URI("#section");
            userInfoUri = new URI("https://user:pass@example.com:8443/p/q?x=1#f");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public URI getUri() {
        return uri;
    }

    // ===== proposed edge-cases — getters =====

    public URI getOpaqueUri() {
        return opaqueUri;
    }

    public URI getUrnUri() {
        return urnUri;
    }

    public URI getRelativeUri() {
        return relativeUri;
    }

    public URI getIpv6Uri() {
        return ipv6Uri;
    }

    public URI getEncodedUri() {
        return encodedUri;
    }

    public URI getFragmentOnlyUri() {
        return fragmentOnlyUri;
    }

    public URI getUserInfoUri() {
        return userInfoUri;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        URIData copy = (URIData) o;
        assertAll("URI tests",
                () -> assertEquals(this.getUri(), copy.getUri(), "java.net.URI"),

                // ===== proposed edge-case verifications =====
                // URI.equals is value-based (case-insensitive on scheme/host, decoded on path/query).
                // We additionally assert toString() equality to catch any lossy normalization that
                // URI.equals would mask.
                () -> {
                    if (this.getOpaqueUri() != null) {
                        assertEquals(this.getOpaqueUri(), copy.getOpaqueUri(), "opaque URI (mailto:)");
                        assertEquals(this.getOpaqueUri().toString(), copy.getOpaqueUri().toString(), "opaque URI raw form");
                    } else {
                        assertNull(copy.getOpaqueUri());
                    }
                },
                () -> {
                    if (this.getUrnUri() != null) {
                        assertEquals(this.getUrnUri(), copy.getUrnUri(), "URN (urn:isbn:…)");
                        assertEquals(this.getUrnUri().toString(), copy.getUrnUri().toString(), "URN raw form");
                    } else {
                        assertNull(copy.getUrnUri());
                    }
                },
                () -> {
                    if (this.getRelativeUri() != null) {
                        assertEquals(this.getRelativeUri(), copy.getRelativeUri(), "relative URI (no scheme)");
                        assertEquals(this.getRelativeUri().toString(), copy.getRelativeUri().toString(), "relative URI raw form");
                    } else {
                        assertNull(copy.getRelativeUri());
                    }
                },
                () -> {
                    if (this.getIpv6Uri() != null) {
                        assertEquals(this.getIpv6Uri(), copy.getIpv6Uri(), "IPv6 host URI");
                        assertEquals(this.getIpv6Uri().toString(), copy.getIpv6Uri().toString(), "IPv6 host raw form");
                    } else {
                        assertNull(copy.getIpv6Uri());
                    }
                },
                () -> {
                    if (this.getEncodedUri() != null) {
                        assertEquals(this.getEncodedUri(), copy.getEncodedUri(), "percent-encoded URI");
                        assertEquals(this.getEncodedUri().toString(), copy.getEncodedUri().toString(), "encoded URI raw form (escapes preserved)");
                    } else {
                        assertNull(copy.getEncodedUri());
                    }
                },
                () -> {
                    if (this.getFragmentOnlyUri() != null) {
                        assertEquals(this.getFragmentOnlyUri(), copy.getFragmentOnlyUri(), "fragment-only URI");
                        assertEquals(this.getFragmentOnlyUri().toString(), copy.getFragmentOnlyUri().toString(), "fragment-only raw form");
                    } else {
                        assertNull(copy.getFragmentOnlyUri());
                    }
                },
                () -> {
                    if (this.getUserInfoUri() != null) {
                        assertEquals(this.getUserInfoUri(), copy.getUserInfoUri(), "full-structure URI (userInfo+port+query+fragment)");
                        assertEquals(this.getUserInfoUri().toString(), copy.getUserInfoUri().toString(), "full-structure raw form");
                    } else {
                        assertNull(copy.getUserInfoUri());
                    }
                }
        );
    }
}
