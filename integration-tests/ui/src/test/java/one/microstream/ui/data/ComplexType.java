package one.microstream.ui.data;

/*-
 * #%L
 * EclipseStore Integration Tests - UI
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

public class ComplexType implements BinaryHandlerTestData {

    private BasicNonPrimitive basicNonPrimitive = new BasicNonPrimitive();
    private Integer integer = 0;
    private LongList longList = new LongList();


    @Override
    public void fillSampleData() {
        basicNonPrimitive.fillSampleData();
        integer = 30;
        longList.fillSampleData();
    }
}
