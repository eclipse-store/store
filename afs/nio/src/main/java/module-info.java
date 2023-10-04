/*-
 * #%L
 * EclipseStore Abstract File System - Java NIO
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
module org.eclipse.store.afs.nio
{
	exports org.eclipse.store.afs.nio.types;

	requires transitive org.eclipse.serializer.afs;
}
