/*-
 * #%L
 * Eclipse Store Base utilities
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
module org.eclipse.store.base
{

    exports org.eclipse.store.base.collections;
    exports org.eclipse.store.base.concurrency;
    exports org.eclipse.store.base.exception;
    exports org.eclipse.store.base.io;
    exports org.eclipse.store.base.chars;
    exports org.eclipse.store.base.functional;
    exports org.eclipse.store.base.math;
    exports org.eclipse.store.base.memory;
    exports org.eclipse.store.base.reference;
    exports org.eclipse.store.base.typing;
    exports org.eclipse.store.base.util;
    exports org.eclipse.store.base;

    requires org.eclipse.serializer.base;
}
