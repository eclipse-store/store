/*-
 * #%L
 * Eclipse Storage REST Service Sparkjava
 * %%
 * Copyright (C) 2019 - 2023 Eclipse Foundation
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
module org.eclipse.store.restservice.sparkjava
{
	exports org.eclipse.storage.restservice.sparkjava.exceptions;
	exports org.eclipse.storage.restservice.sparkjava.types;
	
	requires com.google.gson;
	requires org.eclipse.store.base;
	requires org.eclipse.store.storage;
	requires org.eclipse.store.restadapter;
	requires org.eclipse.store.restservice;
	requires spark.core;
}
