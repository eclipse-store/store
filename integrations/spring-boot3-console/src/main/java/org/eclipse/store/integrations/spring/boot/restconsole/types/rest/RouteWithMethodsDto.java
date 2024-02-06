package org.eclipse.store.integrations.spring.boot.restconsole.types.rest;

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

