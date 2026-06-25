package test.eclipse.store.export;

/*-
 * #%L
 * EclipseStore Integration Tests
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import net.datafaker.Faker;

public class CustomerGenerator {

    private static Faker faker = new Faker();

    private CustomerGenerator() {
        // prevent instantiate
    }

    public static Customer generateNewCustomer() {
        return new Customer(faker.name().firstName(), faker.name().lastName(), faker.address().streetName(), faker.address().streetAddressNumber());
    }
}
