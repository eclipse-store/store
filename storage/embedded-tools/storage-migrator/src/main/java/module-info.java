/*-
 * #%L
 * EclipseStore Storage Embedded Tools Storage Migrator
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */
module org.eclipse.store.storage.embedded.tools.storage.migrator
{
	exports org.eclipse.store.storage.embedded.tools.storage.migrator;
	exports org.eclipse.store.storage.embedded.tools.storage.migrator.mappings;
	exports org.eclipse.store.storage.embedded.tools.storage.migrator.typedictionary;

	requires transitive org.eclipse.serializer.persistence.binary;
	requires transitive com.fasterxml.jackson.annotation;
	requires transitive java.object.diff;
	requires transitive lombok;
	requires transitive micrometer.core;
	requires transitive rewrite.core;
	requires transitive rewrite.java;
	requires transitive rewrite.maven;
}
