package org.eclipse.store.examples.eagerstoring;

/*-
 * #%L
 * microstream-examples-eager-storing
 * %%
 * Copyright (C) 2019 - 2022 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MyRoot
{
	@StoreEager
	final List<Integer> numbers = new ArrayList<>();
	
	final List<LocalDateTime> dates = new ArrayList<>();
}
