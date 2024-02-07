package test.eclipse.store.storage.restservice.spring.boot.types;

/*-
 * #%L
 * integrations-spring-boot3-itest
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

import org.assertj.core.util.Lists;
import org.eclipse.store.storage.restadapter.types.*;
import org.eclipse.store.storage.restservice.spring.boot.types.configuration.StoreDataRestServiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import test.eclipse.store.integration.spring.boot.ITestApplication;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
    classes = ITestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc
@ActiveProfiles("web-itest")
class StoreDataResControllerTest {

  @Autowired
  private MockMvc rest;

  @Autowired
  private StoreDataRestServiceProperties properties;

  @MockBean
  private StorageRestAdapter storageRestAdapter;

  @Test
  public void provides_links() throws Exception {
    var baseUrl = properties.getBaseUrl();
    rest
        .perform(get(properties.getBaseUrl() + "/"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.size()").value("5"))
        .andExpect(jsonPath("$.[0].URL").value(baseUrl + "/"))
        .andExpect(jsonPath("$.[1].URL").value(baseUrl + "/root"))
        .andExpect(jsonPath("$.[2].URL").value(baseUrl + "/dictionary"))
        .andExpect(jsonPath("$.[3].URL").value(baseUrl + "/object/:oid"))
        .andExpect(jsonPath("$.[4].URL").value(baseUrl + "/maintenance/filesStatistics"))
    ;
  }

  @Test
  public void provides_root() throws Exception {
    var objectId = 81276318L;
    when(storageRestAdapter.getUserRoot())
        .thenReturn(
            new ViewerRootDescription("custom", objectId)
        );

    rest
        .perform(get(properties.getBaseUrl() + "/root"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").exists())
        .andExpect(jsonPath("$.name").value("custom"))
        .andExpect(jsonPath("$.objectId").value(objectId))
    ;

    verify(storageRestAdapter).getUserRoot();
    verifyNoMoreInteractions(storageRestAdapter);
  }

  @Test
  public void provides_object() throws Exception {
    var objectId = 81276318L;
    var objectDescription = new ViewerObjectDescription();
    objectDescription.setObjectId("" + objectId);
    objectDescription.setTypeId("100000001");
    when(storageRestAdapter.getObject(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyBoolean()))
        .thenReturn(
            objectDescription
        );

    rest
        .perform(get(properties.getBaseUrl() + "/object/" + objectId))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").exists())
        .andExpect(jsonPath("$.objectId").value(objectDescription.getObjectId()))
        .andExpect(jsonPath("$.typeId").value(objectDescription.getTypeId()))
    ;

    verify(storageRestAdapter).getObject(
        objectId, 0L, Long.MAX_VALUE, 0L, Long.MAX_VALUE,  Long.MAX_VALUE, false
    );
    verifyNoMoreInteractions(storageRestAdapter);
  }

  @Test
  public void provides_stats() throws Exception {

    var now = Instant.now();
    var channels = new HashMap<Integer, ViewerChannelStatistics>();
    channels.put(1, new ViewerChannelStatistics(1, 1, 1200L, 2300L, Lists.newArrayList(
        new ViewerFileStatistics(1L, 1200L, 2300L, 0L, "myFile")
    )));
    channels.put(2, new ViewerChannelStatistics(2, 1, 34L, 45L, Lists.newArrayList(
        new ViewerFileStatistics(1L, 34L, 45L, 0L, "otherFile")
    )));

    when(storageRestAdapter.getStorageFilesStatistics())
        .thenReturn(
            new ViewerStorageFileStatistics(
                Date.from(now),
                2L,
                1234L,
                2345L,
                channels
            )
        );

    rest
        .perform(get(properties.getBaseUrl() + "/maintenance/filesStatistics"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").exists())
        .andExpect(jsonPath("$.creationTime").exists())
        .andExpect(jsonPath("$.fileCount").value(2))
        .andExpect(jsonPath("$.liveDataLength").value(1234))
        .andExpect(jsonPath("$.totalDataLength").value(2345))
        .andExpect(jsonPath("$.channelStatistics").isMap())
        .andExpect(jsonPath("$.channelStatistics.size()").value(2))
    ;

    verify(storageRestAdapter).getStorageFilesStatistics();
    verifyNoMoreInteractions(storageRestAdapter);
  }

}
