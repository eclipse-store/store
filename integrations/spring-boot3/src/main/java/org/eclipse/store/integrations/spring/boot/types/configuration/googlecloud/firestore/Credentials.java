package org.eclipse.store.integrations.spring.boot.types.configuration.googlecloud.firestore;

/*-
 * #%L
 * spring-boot3
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

public class Credentials
{
    /**
     * The type of the credentials provider. Supported values are:
     *
     * <ul>
     * <li>"none": No credentials are used.</li>
     * <li>"input-stream": Path of a JSON file stream. The stream can contain a Service Account key file in JSON format from the Google Developers Console or a stored user credential using the format supported by the Cloud SDK.</li>
     * <li>"default": Returns the Application Default Credentials which are used to identify and authorize the whole application. The following are searched (in order) to find the Application Default Credentials:
     *   <ul>
     *     <li>Credentials file pointed to by the GOOGLE_APPLICATION_CREDENTIALS environment variable.</li>
     *     <li>Credentials provided by the Google Cloud SDK.</li>
     *     <ul>
     *          <li>gcloud auth application-default login for user account credentials.</li>
     *          <li>gcloud auth application-default login --impersonate-service-account for impersonated service account credentials.</li>
     *     </ul>
     *     <li>Google App Engine built-in credentials.</li>
     *     <li>Google Cloud Shell built-in credentials.</li>
     *     <li>Google Compute Engine built-in credentials.</li>
     *   </ul>
     * </li>
     * </ul>
     */
    private String type;

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }
}
