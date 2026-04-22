package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.serializer.persistence.types.PersistenceObjectIdAcceptor;

/**
 * Iterates over every currently live (non-cleared) application-held object id and passes each one
 * to the given acceptor. Used by the storage GC to seed mark roots from the application object
 * registry so that safety-net-kept entities have their binary references transitively marked,
 * preventing zombie OIDs caused by stale binary records referencing swept entities.
 */
@FunctionalInterface
public interface LiveObjectIdsIterator
{
	public void iterateLiveObjectIds(PersistenceObjectIdAcceptor acceptor);
}
