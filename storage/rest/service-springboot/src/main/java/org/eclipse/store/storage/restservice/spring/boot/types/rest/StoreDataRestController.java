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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.springframework.http.ResponseEntity.notFound;
import static org.springframework.http.ResponseEntity.ok;

/**
 * Spring REST controller exposing storage data for the restclient.
 */
@RestController
@RequestMapping(path = {"${org.eclipse.store.rest.base-url:#{T(org.eclipse.store.storage.restservice.spring.boot.types.configuration.StoreDataRestServiceProperties).DEFAULT_BASE_URL}}"}, produces = {MediaType.APPLICATION_JSON_VALUE})
public class StoreDataRestController {
  private final Map<String, StorageRestAdapter> adapters;
  private final StoreDataRestServiceProperties properties;
  private final Logger logger = LoggerFactory.getLogger(StoreDataRestController.class);

  public StoreDataRestController(
      Map<String, StorageRestAdapter> adapters,
      StoreDataRestServiceProperties properties
  ) {
    if (adapters.size() == 1 && adapters.containsKey("defaultStorageManager")) {
      this.adapters = Map.of("default", adapters.get("defaultStorageManager"));
    } else {
      this.adapters = adapters;
    }
    this.properties = properties;
  }

  @PostConstruct
  public void validateSetup() {
    Objects.requireNonNull(properties.getBaseUrl(), "Base URL must not be null. ");
    adapters.keySet().forEach(adapter ->
        logger.info("[ECLIPSE STORE REST SERVICE]: Configured storage adapter: '{}/{}/'", properties.getBaseUrl(), adapter)
    );
  }

  @GetMapping(value = "/")
  public ResponseEntity<List<RouteWithMethodsDto>> getAvailableRoutes() {
    var prefix = properties.getBaseUrl();
    var routes = new ArrayList<RouteWithMethodsDto>();
    adapters.keySet().forEach(storageName ->
        routes.addAll(
            Stream.of(
                new RouteWithMethodsDto(prefix + "/" + storageName + "/", HttpMethod.GET.name().toLowerCase()),
                new RouteWithMethodsDto(prefix + "/" + storageName + "/root", HttpMethod.GET.name().toLowerCase()),
                new RouteWithMethodsDto(prefix + "/" + storageName + "/dictionary", HttpMethod.GET.name().toLowerCase()),
                new RouteWithMethodsDto(prefix + "/" + storageName + "/object/:oid", HttpMethod.GET.name().toLowerCase()),
                new RouteWithMethodsDto(prefix + "/" + storageName + "/maintenance/filesStatistics", HttpMethod.GET.name().toLowerCase())
            ).toList()
        )
    );
    return ok(routes);
  }


  @GetMapping(value = "/{storageName}/root")
  public ResponseEntity<ViewerRootDescription> getRoot(@PathVariable("storageName") String storageName) {
    if (adapters.containsKey(storageName)) {
      var root = adapters.get(storageName).getUserRoot();
      return ok(root);
    } else {
      return notFound().build();
    }
  }

  @GetMapping(value = "/{storageName}/dictionary")
  public ResponseEntity<String> getDictionary(@PathVariable("storageName") String storageName) {
    if (adapters.containsKey(storageName)) {
      var dictionary = adapters.get(storageName).getTypeDictionary();
      return ok(dictionary);
    } else {
      return notFound().build();
    }
  }

  @GetMapping("/{storageName}/object/{oid}")
  public ResponseEntity<ViewerObjectDescription> getObject(
      @PathVariable("storageName") String storageName,
      @PathVariable(value = "oid") Long objectId,
      @RequestParam(value = "valueLength", required = false) Long valueLength,
      @RequestParam(value = "fixedOffset", required = false) Long fixedOffset,
      @RequestParam(value = "fixedLength", required = false) Long fixedLength,
      @RequestParam(value = "variableOffset", required = false) Long variableOffset,
      @RequestParam(value = "variableLength", required = false) Long variableLength,
      @RequestParam(value = "references", required = false) Boolean resolveReferences
  ) {
    if (adapters.containsKey(storageName)) {
      var storageObject = adapters.get(storageName).getObject(
          objectId,
          fixedOffset != null ? fixedOffset : 0L,
          fixedLength != null ? fixedLength : Long.MAX_VALUE,
          variableOffset != null ? variableOffset : 0L,
          variableLength != null ? variableLength : Long.MAX_VALUE,
          valueLength != null ? valueLength : Long.MAX_VALUE,
          resolveReferences != null ? resolveReferences : false
      );
      return ok(storageObject);
    } else {
      return notFound().build();
    }
  }

  @GetMapping("/{storageName}/maintenance/filesStatistics")
  public ResponseEntity<ViewerStorageFileStatistics> getFileStatistics(@PathVariable("storageName") String storageName) {
    if (adapters.containsKey(storageName)) {
      var stats = adapters.get(storageName).getStorageFilesStatistics();
      return ok(stats);
    } else {
      return notFound().build();
    }
  }
}
