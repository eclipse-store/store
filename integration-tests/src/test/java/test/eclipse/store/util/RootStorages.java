package test.eclipse.store.util;

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

import java.nio.file.Path;
import java.util.function.Supplier;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

/**
 * Starting a storage on a root of any type.
 * <p>
 * {@code EmbeddedStorage.start(root, dir)} sets the root explicitly, which means the persisted state
 * is applied to the very instance handed over - impossible for an instance without identity, so it is
 * refused for one. Installing the root through a supplier says nothing about applying state to it and
 * therefore serves both kinds, which is what a test storing a {@code java.time} instance as its root
 * needs where those types are value classes.
 */
public final class RootStorages
{
    /**
     * @param dir  the storage directory.
     * @param root the instance to install as the root; may have identity or not.
     *
     * @return a started storage whose root is the passed instance.
     */
    public static EmbeddedStorageManager startWithRoot(final Path dir, final Object root)
    {
        return startWithSuppliedRoot(dir, () -> root);
    }

    /**
     * The same for a root that cannot be resolved yet, which is the case the supplier exists for.
     *
     * @param dir          the storage directory.
     * @param rootSupplier resolves the root when the storage asks for it.
     *
     * @return a started storage whose root is what the supplier resolves to.
     */
    public static EmbeddedStorageManager startWithSuppliedRoot(final Path dir, final Supplier<?> rootSupplier)
    {
        return EmbeddedStorage
            .Foundation(dir)
            .onConnectionFoundation(cf ->
                cf.getRootResolverProvider().rootReference().setRootSupplier(rootSupplier)
            )
            .start()
        ;
    }

    private RootStorages()
    {
        throw new UnsupportedOperationException();
    }
}
