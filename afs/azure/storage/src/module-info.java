/*-
 * #%L
 * afs-azure-storage
 * %%
 * Copyright (C) 2023 Eclipse Foundation
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
module org.eclipse.store.afs.azure.storage
{
	exports org.eclipse.storage.afs.azure.storage.types;
	
	provides org.eclipse.storage.configuration.types.ConfigurationBasedCreator
	    with org.eclipse.storage.afs.azure.storage.types.AzureStorageFileSystemCreator
	;
	
	requires transitive org.eclipse.store.configuration;
	requires transitive org.eclipse.store.afs.blobstore;
	requires transitive com.azure.core;
	requires transitive com.azure.storage.blob;
	requires transitive com.azure.storage.common;
}
