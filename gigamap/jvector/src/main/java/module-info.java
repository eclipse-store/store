/*-
 * #%L
 * EclipseStore GigaMap JVector
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
/**
 * Module providing vector similarity search integration with EclipseStore's GigaMap.
 * <p>
 * This module enables k-nearest-neighbor (k-NN) similarity search using the HNSW
 * (Hierarchical Navigable Small World) algorithm with GigaMap's persistent storage.
 */
module org.eclipes.store.gigamap.jvector
{
    // EclipseStore GigaMap (transitively provides serializer modules)
    requires transitive org.eclipse.store.gigamap;

    // HNSW implementation (internal dependency)
    requires jvector;

    // Export public API
    exports org.eclipse.store.gigamap.jvector;

    // Open to EclipseStore for reflective access (provideTypeHandler discovery)
    opens org.eclipse.store.gigamap.jvector to org.eclipse.serializer.persistence;
}
