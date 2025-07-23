package org.eclipse.store.gigamap.restart.update;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.types.IndexerBoolean;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.gigamap.types.IndexerString;

public class SmalEVIndices
{
    public final static IndexerString<SmallEV> vin = new IndexerString.Abstract<>()
    {
        public String name()
        {
            return "vin";
        }

        @Override
        protected String getString(final SmallEV entity)
        {
            return entity.getVin();
        }
    };

    public final static IndexerString<SmallEV> make = new IndexerString.Abstract<>()
    {
        public String name()
        {
            return "make";
        }

        @Override
        protected String getString(final SmallEV entity)
        {
            return entity.getMake();
        }
    };

    public final static IndexerString<SmallEV> model = new IndexerString.Abstract<>()
    {
        public String name()
        {
            return "model";
        }

        @Override
        protected String getString(final SmallEV entity)
        {
            return entity.getModel();
        }
    };

    public final static IndexerInteger<SmallEV> year = new IndexerInteger.Abstract<>()
    {
        public String name()
        {
            return "year";
        }

        @Override
        protected Integer getInteger(SmallEV entity)
        {
            return entity.getYear();
        }
    };

    public final static IndexerBoolean<SmallEV> electric = new IndexerBoolean.Abstract<>()
    {
        public String name()
        {
            return "electric";
        }

        @Override
        protected Boolean getBoolean(SmallEV entity)
        {
            return entity.getElectric();
        }
    };

}
