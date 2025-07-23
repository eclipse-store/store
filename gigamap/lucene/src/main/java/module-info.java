/*-
 * #%L
 * EclipseStore GigaMap Lucene
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
module org.eclipse.store.gigamap.lucene
{
	exports org.eclipse.store.gigamap.lucene;
	
	requires org.apache.lucene.core;
	requires org.apache.lucene.queryparser;
	requires org.eclipse.serializer.base;
	requires org.eclipse.store.gigamap;
	
	opens org.eclipse.store.gigamap.lucene to org.eclipse.serializer.persistence;
}
