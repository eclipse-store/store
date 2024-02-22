package org.microstream.spring.boot.example.advanced.service;

/*-
 * #%L
 * EclipseStore Example Spring Boot3 Advanced
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.util.List;

public interface MuppetsInPort {

  String getMuppet(Integer id);

  List<String> getAllMuppets();

  void initialize();

}
