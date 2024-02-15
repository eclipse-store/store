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
module org.eclipse.store.storage
{
	exports org.eclipse.store.storage.util;
	exports org.eclipse.store.storage.types;
	exports org.eclipse.store.storage.exceptions;
	exports org.eclipse.store.storage.monitoring;

	requires transitive org.eclipse.store.afs.nio;
	requires transitive org.eclipse.serializer.persistence.binary;
	requires transitive org.eclipse.serializer.base;
}
