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

import jakarta.annotation.PostConstruct;
import org.eclipse.store.storage.restadapter.types.StorageRestAdapter;
import org.eclipse.store.storage.restadapter.types.ViewerObjectDescription;
import org.eclipse.store.storage.restadapter.types.ViewerRootDescription;
import org.eclipse.store.storage.restadapter.types.ViewerStorageFileStatistics;
import org.eclipse.store.storage.restservice.spring.boot.types.configuration.StoreDataRestServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.springframework.http.ResponseEntity.ok;

/**
 * Spring REST controller exposing storage data for the restclient.
 */
@RestController
@RequestMapping(path = {"${org.eclipse.store.rest.base-url:#{T(org.eclipse.store.storage.restservice.spring.boot.types.configuration.StoreDataRestServiceProperties).DEFAULT_BASE_URL}}"}, produces = {MediaType.APPLICATION_JSON_VALUE})
public class StoreDataRestController {
  private final StorageRestAdapter adapter;
  private final StoreDataRestServiceProperties properties;
  private final Logger logger = LoggerFactory.getLogger(StoreDataRestController.class);

  public StoreDataRestController(
      StorageRestAdapter adapter,
      StoreDataRestServiceProperties properties
  ) {
    this.adapter = adapter;
    this.properties = properties;
  }

  @PostConstruct
  public void validateSetup() {
    Objects.requireNonNull(properties.getBaseUrl(), "Base URL must not be null. ");
    logger.info("[ECLIPSE STORE REST SERVICE]: Started service using '{}/'", properties.getBaseUrl());
  }

  @GetMapping(value = "/")
  public ResponseEntity<List<RouteWithMethodsDto>> getAvailableRoutes() {
    var prefix = properties.getBaseUrl();
    var routes = Stream.of(
        new RouteWithMethodsDto(prefix + "/", HttpMethod.GET.name().toLowerCase()),
        new RouteWithMethodsDto(prefix + "/root", HttpMethod.GET.name().toLowerCase()),
        new RouteWithMethodsDto(prefix + "/dictionary", HttpMethod.GET.name().toLowerCase()),
        new RouteWithMethodsDto(prefix + "/object/:oid", HttpMethod.GET.name().toLowerCase()),
        new RouteWithMethodsDto(prefix + "/maintenance/filesStatistics", HttpMethod.GET.name().toLowerCase())
    ).toList();
    return ok(routes);
  }

  @GetMapping(value = "/root")
  public ResponseEntity<ViewerRootDescription> getRoot() {
    var root = adapter.getUserRoot();
    return ok(root);
  }

  @GetMapping(value = "/dictionary")
  public ResponseEntity<String> getDictionary() {
    var dictionary = adapter.getTypeDictionary();
    return ok(dictionary);
  }

  @GetMapping("/object/{oid}")
  public ResponseEntity<ViewerObjectDescription> getObject(
      @PathVariable(value = "oid") Long objectId,
      @RequestParam(value = "valueLength", required = false) Long valueLength,
      @RequestParam(value = "fixedOffset", required = false) Long fixedOffset,
      @RequestParam(value = "fixedLength", required = false) Long fixedLength,
      @RequestParam(value = "variableOffset", required = false) Long variableOffset,
      @RequestParam(value = "variableLength", required = false) Long variableLength,
      @RequestParam(value = "references", required = false) Boolean resolveReferences
  ) {
    var storageObject = adapter.getObject(
        objectId,
        fixedOffset != null ? fixedOffset : 0L,
        fixedLength != null ? fixedLength : Long.MAX_VALUE,
        variableOffset != null ? variableOffset : 0L,
        variableLength != null ? variableLength : Long.MAX_VALUE,
        valueLength != null ? valueLength : Long.MAX_VALUE,
        resolveReferences != null ? resolveReferences : false
    );
    return ok(storageObject);
  }

  @GetMapping("/maintenance/filesStatistics")
  public ResponseEntity<ViewerStorageFileStatistics> getFileStatistics() {
    var stats = adapter.getStorageFilesStatistics();
    return ok(stats);
  }
}
