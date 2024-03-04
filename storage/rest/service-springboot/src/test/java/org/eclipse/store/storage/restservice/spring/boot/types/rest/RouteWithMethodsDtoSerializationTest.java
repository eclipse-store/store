package org.eclipse.store.storage.restservice.spring.boot.types.rest;

/*-
 * #%L
 * integrations-spring-boot3-console
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteWithMethodsDtoSerializationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void should_serialize_correctly_applying_jackson_annotations() throws Exception {
    var dto = new RouteWithMethodsDto("/path", "get");
    var json = mapper.writeValueAsString(dto);
    assertThat(json).isEqualTo("{\"URL\":\"/path\",\"HttpMethod\":\"get\"}");
  }
}
