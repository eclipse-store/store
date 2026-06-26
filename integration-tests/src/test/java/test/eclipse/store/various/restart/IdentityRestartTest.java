package test.eclipse.store.various.restart;

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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Identity / object-id continuity across shutdown/start cycles. {@code shutdown()} truncates the
 * object registry, so these tests guard against the registry reload after a restart duplicating
 * shared instances, breaking enum identity, or resetting the object-id counter (which would let a
 * post-restart store overwrite a pre-restart object).
 */
public class IdentityRestartTest
{
    static class Shared
    {
        String data;

        Shared(final String data)
        {
            this.data = data;
        }
    }

    static class Holder
    {
        Shared left;
        Shared right;
    }

    enum Color
    {
        RED, GREEN, BLUE
    }

    static class ColorBox
    {
        Color color;

        ColorBox(final Color color)
        {
            this.color = color;
        }
    }

    static class Node
    {
        int id;
        Node prev;

        Node(final int id, final Node prev)
        {
            this.id = id;
            this.prev = prev;
        }
    }

    @Test
    public void sharedReference_identityPreserved_afterRestart(@TempDir final Path dir)
    {
        final Holder holder = new Holder();
        final Shared shared = new Shared("shared-data");
        holder.left = shared;
        holder.right = shared;

        final EmbeddedStorageManager storage = EmbeddedStorage.start(holder, dir);
        storage.storeRoot();
        storage.shutdown();

        storage.start();
        final Holder afterRestart = storage.root();
        assertSame(afterRestart.left, afterRestart.right,
                "shared reference must stay a single instance after same-instance restart");
        storage.shutdown();
        // fresh manager: the loader must rebuild the shared reference as one instance from disk
        final EmbeddedStorageManager fresh = EmbeddedStorage.start(new Holder(), dir);
        final Holder fromDisk = fresh.root();
        assertNotNull(fromDisk.left);
        assertSame(fromDisk.left, fromDisk.right,
                "shared reference must be a single instance when loaded fresh from disk");
        assertEquals("shared-data", fromDisk.left.data);
        fresh.shutdown();
    }

    @Test
    public void enumIdentity_afterRestart(@TempDir final Path dir)
    {
        final List<ColorBox> boxes = new ArrayList<>();
        boxes.add(new ColorBox(Color.RED));
        boxes.add(new ColorBox(Color.GREEN));
        boxes.add(new ColorBox(Color.RED));

        final EmbeddedStorageManager storage = EmbeddedStorage.start(boxes, dir);
        storage.storeRoot();
        storage.shutdown();
        storage.start();
        storage.shutdown();

        // fresh load must resolve enums to their canonical constants (== identity)
        final EmbeddedStorageManager fresh = EmbeddedStorage.start(new ArrayList<ColorBox>(), dir);
        final List<ColorBox> fromDisk = fresh.root();
        assertEquals(3, fromDisk.size());
        assertSame(Color.RED, fromDisk.get(0).color, "enum must resolve to the canonical constant");
        assertSame(Color.GREEN, fromDisk.get(1).color);
        assertSame(Color.RED, fromDisk.get(2).color);
        assertSame(fromDisk.get(0).color, fromDisk.get(2).color, "equal enums must be the same instance");
        fresh.shutdown();
    }

    @Test
    public void crossCycleLinkedChain_intactAfterRestarts(@TempDir final Path dir)
    {
        final int chainLength = 12;
        final List<Node> head = new ArrayList<>(); // head.get(0) is the tip of the chain

        final EmbeddedStorageManager storage = EmbeddedStorage.start(head, dir);

        Node prev = null;
        for (int i = 0; i < chainLength; i++) {
            // each cycle links a new node to the node stored in the previous cycle, then restarts
            final Node node = new Node(i, prev);
            if (head.isEmpty()) {
                head.add(node);
            } else {
                head.set(0, node);
            }
            storage.store(head);
            prev = node;
            storage.shutdown();
            storage.start();
        }
        storage.shutdown();

        // fresh manager: walk the chain from disk and confirm every cross-cycle link survived,
        // each node is a distinct instance, and ids are intact (no oid collision/overwrite)
        final EmbeddedStorageManager fresh = EmbeddedStorage.start(new ArrayList<Node>(), dir);
        final List<Node> fromDisk = fresh.root();
        Node n = fromDisk.get(0);
        final List<Node> seen = new ArrayList<>();
        for (int expectedId = chainLength - 1; expectedId >= 0; expectedId--) {
            assertNotNull(n, "chain truncated at expected id " + expectedId);
            assertEquals(expectedId, n.id, "node id along the chain");
            for (final Node s : seen) {
                assertNotSame(s, n, "each chain node must be a distinct instance");
            }
            seen.add(n);
            n = n.prev;
        }
        assertEquals(chainLength, seen.size(), "chain must contain every node stored across cycles");
        fresh.shutdown();
    }
}
