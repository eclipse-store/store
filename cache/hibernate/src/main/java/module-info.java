/*-
 * #%L
 * EclipseStore Cache for Hibernate
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
module org.eclipse.store.cache.hibernate
{
	exports org.eclipse.store.cache.hibernate.types;
	
	requires transitive org.eclipse.store.cache;
	requires transitive java.naming;
	requires transitive java.persistence;
	requires transitive org.hibernate.orm.core;
	requires org.eclipse.serializer.base;
}
