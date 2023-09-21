
package org.eclipse.store.storage.restclient.types;

/*-
 * #%L
 * EclipseStore Storage REST Client
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

import java.util.Map;

import org.eclipse.serializer.persistence.types.PersistenceTypeDescription;
import org.eclipse.store.storage.restadapter.types.ViewerObjectDescription;
import org.eclipse.store.storage.restadapter.types.ViewerRootDescription;
import org.eclipse.store.storage.restadapter.types.ViewerStorageFileStatistics;


public interface StorageRestClient extends AutoCloseable
{
	public Map<Long, PersistenceTypeDescription> requestTypeDictionary();
	
	public ViewerRootDescription requestRoot();
	
	public ViewerObjectDescription requestObject(
		ObjectRequest objectRequest
	);
	
	public ViewerStorageFileStatistics requestFileStatistics();
	
	@Override
	public void close();
	
}
