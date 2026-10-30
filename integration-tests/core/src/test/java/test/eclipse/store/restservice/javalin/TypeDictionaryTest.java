package test.eclipse.store.restservice.javalin;

/*-
 * #%L
 * EclipseStore Integration Tests
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.eclipse.serializer.collections.types.XGettingSequence;
import org.eclipse.serializer.collections.types.XGettingTable;
import org.eclipse.serializer.persistence.binary.types.BinaryFieldLengthResolver;
import org.eclipse.serializer.persistence.types.*;
import org.eclipse.serializer.reflect.ClassLoaderProvider;
import org.junit.jupiter.api.Test;

class TypeDictionaryTest extends AbstractIntegrationTest
{

    static final String TypeDictionaryURL = "http://localhost:4567/store-data/dictionary";


    @Test
    void getTypeDictionary() throws IOException
    {
        AbstractIntegrationTest.checkStatusCodeForUrl(TypeDictionaryURL, HttpStatus.SC_OK);
    }

    @Test
    void getAndParseTypeDictionary() throws IOException, ParseException
	{
        final String typeDictionaryString = getAsString(TypeDictionaryURL);

        final ClassLoaderProvider classLoaderProvider = ClassLoaderProvider.System();
        final PersistenceTypeResolver typeResolver = PersistenceTypeResolver.New(classLoaderProvider);
        final PersistenceFieldLengthResolver fieldLengthResolver = new BinaryFieldLengthResolver.Default();
        final PersistenceTypeDictionaryParser parser = PersistenceTypeDictionaryParser.New(typeResolver, fieldLengthResolver, PersistenceTypeNameMapper.New());
        final XGettingSequence<? extends PersistenceTypeDictionaryEntry> parsedDictionaryEntries = parser.parseTypeDictionaryEntries(typeDictionaryString);

        final Map<Long, PersistenceTypeDictionaryEntry> transferredTypeEntries = new HashMap<>();
        for (PersistenceTypeDictionaryEntry parsedDictionaryEntry : parsedDictionaryEntries) {
            transferredTypeEntries.put(parsedDictionaryEntry.typeId(), parsedDictionaryEntry);
        }


        final XGettingTable<Long, PersistenceTypeDefinition> org = storage.typeDictionary()
                .allTypeDefinitions();

        org.iterate(f -> {
            final Long key = f.key();
            final PersistenceTypeDefinition expected = f.value();

            final PersistenceTypeDictionaryEntry actual = transferredTypeEntries.get(key);

            final boolean descriptionEqual = PersistenceTypeDescription.equalDescription(actual, expected);
            assertTrue(descriptionEqual, "TypeId: " + key + " descriptionEqual failed");

            final boolean structureEqual = PersistenceTypeDescription.equalStructure(actual, expected);
            assertTrue(structureEqual, "TypeId: " + key + " structureEqual failed");

            final boolean typeIdEqual = actual.typeId() == expected.typeId();
            assertTrue(typeIdEqual, "TypeId: " + key + " typeIdEqual failed");

        });
    }


}
