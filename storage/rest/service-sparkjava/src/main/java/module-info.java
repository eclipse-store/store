/*-
 * #%L
 * Eclipse Storage REST Service Sparkjava
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
module org.eclipse.store.storage.restservice.sparkjava
{
	exports org.eclipse.store.storage.restservice.sparkjava.exceptions;
	exports org.eclipse.store.storage.restservice.sparkjava.types;
	
	provides org.eclipse.store.storage.restadapter.types.StorageViewDataConverter
	    with org.eclipse.store.storage.restservice.sparkjava.types.StorageViewDataConverterJson
	;
	provides org.eclipse.store.storage.restservice.types.StorageRestServiceProvider
	    with org.eclipse.store.storage.restservice.sparkjava.types.StorageRestServiceProviderSparkJava
	;

	requires transitive org.eclipse.store.storage.restservice;
	requires transitive com.google.gson;
	requires transitive spark.core;
}
