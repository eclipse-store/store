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

import org.eclipse.store.storage.restadapter.types.StorageRestAdapter;
import org.eclipse.store.storage.restadapter.types.ViewerObjectDescription;
import org.eclipse.store.storage.restadapter.types.ViewerRootDescription;
import org.eclipse.store.storage.restadapter.types.ViewerStorageFileStatistics;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Stream;

import static org.eclipse.store.storage.restservice.spring.boot.types.rest.StorageDataRestRestController.STATIC_URL_PREFIX;
import static org.springframework.http.ResponseEntity.ok;

/**
 * Spring REST controller exposing storage data for the restclient.
 */
@RestController
@RequestMapping(value = { STATIC_URL_PREFIX }, produces = {MediaType.APPLICATION_JSON_VALUE})
public class StorageDataRestRestController {
  public static final String STATIC_URL_PREFIX = "/status/store-data";
  private final StorageRestAdapter adapter;

  public StorageDataRestRestController(
      StorageRestAdapter adapter
  ) {
    this.adapter = adapter;
  }

  @GetMapping(value = "/")
  public ResponseEntity<List<RouteWithMethodsDto>> getAvailableRoutes() {
    var routes = Stream.of(
        new RouteWithMethodsDto(STATIC_URL_PREFIX + "/", HttpMethod.GET.name().toLowerCase()),
        new RouteWithMethodsDto(STATIC_URL_PREFIX + "/root", HttpMethod.GET.name().toLowerCase()),
        new RouteWithMethodsDto(STATIC_URL_PREFIX + "/dictionary", HttpMethod.GET.name().toLowerCase()),
        new RouteWithMethodsDto(STATIC_URL_PREFIX + "/object/:oid", HttpMethod.GET.name().toLowerCase()),
        new RouteWithMethodsDto(STATIC_URL_PREFIX + "/maintenance/filesStatistics", HttpMethod.GET.name().toLowerCase())
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
