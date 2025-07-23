/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */
module org.eclipse.store.gigamap
{
    requires transitive org.eclipse.serializer.base;
    requires transitive org.eclipse.serializer.persistence;
    requires transitive org.eclipse.serializer.persistence.binary;
    requires org.slf4j;

    exports org.eclipse.store.gigamap.annotations;
	exports org.eclipse.store.gigamap.exceptions;
	exports org.eclipse.store.gigamap.types;
    opens org.eclipse.store.gigamap.annotations to org.eclipse.serializer.persistence;
	opens org.eclipse.store.gigamap.exceptions to org.eclipse.serializer.persistence;
	opens org.eclipse.store.gigamap.types to org.eclipse.serializer.persistence;
}
