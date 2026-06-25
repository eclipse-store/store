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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class UUIDData implements BinaryHandlerTestData {
    UUID uuid;

    // ===== proposed edge-cases (review & cherry-pick) =====
    // UUID is backed by two longs (mostSigBits + leastSigBits). Round-trip is long-on-long for both
    // halves. Edge-cases probe (a) boundary bit-patterns and (b) half-asymmetry — if the handler
    // swapped or merged the two longs, mostSigOnly vs leastSigOnly would catch it.
    // All values are deterministic literals; randomUUID() is deliberately not used because the
    // compatibility/producer* lane depends on stable sample values across releases.
    private UUID nilUuid;
    private UUID maxUuid;
    private UUID mostSigOnly;
    private UUID leastSigOnly;
    private UUID longBoundaryUuid;

    @Override
    public UUIDData fillSampleData() {
        uuid = UUID.fromString("a96c3d61-0291-4068-b065-1d63d4a94fef");

        // ===== proposed edge-cases =====
        nilUuid = new UUID(0L, 0L);
        maxUuid = new UUID(-1L, -1L);
        mostSigOnly = new UUID(0x0123456789ABCDEFL, 0L);
        leastSigOnly = new UUID(0L, 0x0123456789ABCDEFL);
        longBoundaryUuid = new UUID(Long.MIN_VALUE, Long.MAX_VALUE);

        return this;
    }

    public UUID getUuid() {
        return uuid;
    }

    // ===== proposed edge-cases — getters =====

    public UUID getNilUuid() {
        return nilUuid;
    }

    public UUID getMaxUuid() {
        return maxUuid;
    }

    public UUID getMostSigOnly() {
        return mostSigOnly;
    }

    public UUID getLeastSigOnly() {
        return leastSigOnly;
    }

    public UUID getLongBoundaryUuid() {
        return longBoundaryUuid;
    }

    @Override
    public void proveResults(Object o) {
        assertNotNull(o);
        UUIDData copy = (UUIDData)o;
        assertAll("UUID tests",
                () -> assertEquals(this.getUuid(), copy.getUuid()),

                // ===== proposed edge-case verifications =====
                // UUID.equals compares both halves; assertEquals covers value round-trip end-to-end.
                () -> {
                    if (this.getNilUuid() != null) {
                        assertEquals(new UUID(0L, 0L), copy.getNilUuid(), "nil UUID (both halves = 0)");
                    } else {
                        assertNull(copy.getNilUuid());
                    }
                },
                () -> {
                    if (this.getMaxUuid() != null) {
                        assertEquals(new UUID(-1L, -1L), copy.getMaxUuid(), "max UUID (both halves = -1L)");
                    } else {
                        assertNull(copy.getMaxUuid());
                    }
                },
                () -> {
                    // Half-asymmetry probe: MSB nonzero, LSB zero — detects half-swap
                    if (this.getMostSigOnly() != null) {
                        assertEquals(0x0123456789ABCDEFL, copy.getMostSigOnly().getMostSignificantBits(), "mostSig half preserved");
                        assertEquals(0L, copy.getMostSigOnly().getLeastSignificantBits(), "leastSig half stayed zero");
                    } else {
                        assertNull(copy.getMostSigOnly());
                    }
                },
                () -> {
                    // Half-asymmetry probe: LSB nonzero, MSB zero — detects half-swap
                    if (this.getLeastSigOnly() != null) {
                        assertEquals(0L, copy.getLeastSigOnly().getMostSignificantBits(), "mostSig half stayed zero");
                        assertEquals(0x0123456789ABCDEFL, copy.getLeastSigOnly().getLeastSignificantBits(), "leastSig half preserved");
                    } else {
                        assertNull(copy.getLeastSigOnly());
                    }
                },
                () -> {
                    if (this.getLongBoundaryUuid() != null) {
                        assertEquals(Long.MIN_VALUE, copy.getLongBoundaryUuid().getMostSignificantBits(), "MSB = Long.MIN_VALUE");
                        assertEquals(Long.MAX_VALUE, copy.getLongBoundaryUuid().getLeastSignificantBits(), "LSB = Long.MAX_VALUE");
                    } else {
                        assertNull(copy.getLongBoundaryUuid());
                    }
                }
        );
    }
}
