/*-
 * #%L
 * afs-kafka
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
module org.eclipse.store.afs.kafka
{
	exports org.eclipse.store.afs.kafka.types;
	
	provides org.eclipse.store.configuration.types.ConfigurationBasedCreator
	    with org.eclipse.store.afs.kafka.types.KafkaFileSystemCreator
	;
	
	requires transitive org.eclipse.store.configuration;
	requires transitive org.eclipse.store.afs.blobstore;
	requires transitive kafka.clients;
}
