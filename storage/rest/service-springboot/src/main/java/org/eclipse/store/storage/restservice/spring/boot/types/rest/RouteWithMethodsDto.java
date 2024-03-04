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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO to expose available routes.
 * @param url path
 * @param httpMethod supported method.
 */
public record RouteWithMethodsDto(
  @JsonProperty("URL")
  String url,
  @JsonProperty("HttpMethod")
  String httpMethod
) {

}

