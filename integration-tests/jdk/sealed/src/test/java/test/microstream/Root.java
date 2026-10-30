package test.microstream;

/*-
 * #%L
 * EclipseStore Integration Tests - JDK - Sealed Classes
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

public class Root {

    Car car;
    Truck truck;

    public Root() {
    }

    public Root(Car car, Truck truck) {
        this.car = car;
        this.truck = truck;
    }

    public Car getCar() {
        return car;
    }

    public Truck getTruck() {
        return truck;
    }
}

