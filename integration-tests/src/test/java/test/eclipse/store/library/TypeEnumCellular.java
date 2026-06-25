package test.eclipse.store.library;

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


import test.eclipse.store.library.types.ArrayDequeData;
import test.eclipse.store.library.types.ArrayListData;
import test.eclipse.store.library.types.AtomicPrimitiveData;
import test.eclipse.store.library.types.BasicNonPrimitive;
import test.eclipse.store.library.types.BasicNonPrimitiveArrayTypes;
import test.eclipse.store.library.types.BigDecimalData;
import test.eclipse.store.library.types.BigIntegerData;
import test.eclipse.store.library.types.BinaryHandlerTestData;
import test.eclipse.store.library.types.BitSetData;
import test.eclipse.store.library.types.BulkListData;
import test.eclipse.store.library.types.CalendarApiData;
import test.eclipse.store.library.types.ClassTypeData;
import test.eclipse.store.library.types.ConcurrentHashMapData;
import test.eclipse.store.library.types.ConcurrentLinkedDequeData;
import test.eclipse.store.library.types.ConcurrentLinkedQueueData;
import test.eclipse.store.library.types.ConcurrentSkipListMapData;
import test.eclipse.store.library.types.ConcurrentSkipListSetData;
import test.eclipse.store.library.types.ConstHashEnumData;
import test.eclipse.store.library.types.ConstHashTableData;
import test.eclipse.store.library.types.ConstListData;
import test.eclipse.store.library.types.CopyOnWriteArrayListData;
import test.eclipse.store.library.types.CopyOnWriteArraySetData;
import test.eclipse.store.library.types.CurrencyData;
import test.eclipse.store.library.types.CustomEnumTrivialData;
import test.eclipse.store.library.types.DateData;
import test.eclipse.store.library.types.EqBulkListData;
import test.eclipse.store.library.types.EqConstHashEnumData;
import test.eclipse.store.library.types.EqConstHashTableData;
import test.eclipse.store.library.types.EqHashEnumData;
import test.eclipse.store.library.types.FileData;
import test.eclipse.store.library.types.HashEnumData;
import test.eclipse.store.library.types.HashMapData;
import test.eclipse.store.library.types.HashSetData;
import test.eclipse.store.library.types.HashTableMSData;
import test.eclipse.store.library.types.HashtableData;
import test.eclipse.store.library.types.IntetAddressData;
import test.eclipse.store.library.types.JavaNetURLData;
import test.eclipse.store.library.types.LimitListData;
import test.eclipse.store.library.types.LinkedHashMapData;
import test.eclipse.store.library.types.LinkedHashSetData;
import test.eclipse.store.library.types.LinkedListData;
import test.eclipse.store.library.types.OptionalDoubleData;
import test.eclipse.store.library.types.OptionalIntData;
import test.eclipse.store.library.types.OptionalLongData;
import test.eclipse.store.library.types.PathData;
import test.eclipse.store.library.types.PrimitiveArrayTypes;
import test.eclipse.store.library.types.PrimitiveTypes;
import test.eclipse.store.library.types.PriorityQueueData;
import test.eclipse.store.library.types.PropertiesData;
import test.eclipse.store.library.types.RegexPatternData;
import test.eclipse.store.library.types.RootsData;
import test.eclipse.store.library.types.SocketAddressData;
import test.eclipse.store.library.types.SqlCalendarData;
import test.eclipse.store.library.types.StackData;
import test.eclipse.store.library.types.StringBufferData;
import test.eclipse.store.library.types.StringBuilderData;
import test.eclipse.store.library.types.SubstituterDefaultData;
import test.eclipse.store.library.types.TreeMapData;
import test.eclipse.store.library.types.TreeSetData;
import test.eclipse.store.library.types.URIData;
import test.eclipse.store.library.types.UUIDData;
import test.eclipse.store.library.types.VectorData;
import test.eclipse.store.library.types.WeakHashMapData;

import java.util.function.Supplier;

public enum TypeEnumCellular {
    PrimitiveTypes(new PrimitiveTypes().fillSampleData(), PrimitiveTypes::new),
    ArrayDeque(new ArrayDequeData().fillSampleData(), ArrayDequeData::new),
    ArrayList(new ArrayListData().fillSampleData(), ArrayListData::new),
    BasicNonPrimitiveArrayTypes(new BasicNonPrimitiveArrayTypes().fillSampleData(), BasicNonPrimitiveArrayTypes::new),
    BasicNonPrimitive(new BasicNonPrimitive().fillSampleData(), BasicNonPrimitive::new),
    BigDecimal(new BigDecimalData().fillSampleData(), BigDecimalData::new),
    BigInteger(new BigIntegerData().fillSampleData(), BigIntegerData::new),
    BulkList(new BulkListData().fillSampleData(), BulkListData::new),
    Class(new ClassTypeData().fillSampleData(), ClassTypeData::new),
    ConcurrentHashMap(new ConcurrentHashMapData().fillSampleData(), ConcurrentHashMapData::new),
    ConcurrentLinkedDeque(new ConcurrentLinkedDequeData().fillSampleData(), ConcurrentLinkedDequeData::new),
    ConcurrentLinkedQueue(new ConcurrentLinkedQueueData().fillSampleData(), ConcurrentLinkedQueueData::new),
    ConcurrentSkipListMap(new ConcurrentSkipListMapData().fillSampleData(), ConcurrentSkipListMapData::new),
    ConcurrentSkipListSet(new ConcurrentSkipListSetData().fillSampleData(), ConcurrentSkipListSetData::new),
    ConstHashEnum(new ConstHashEnumData().fillSampleData(), ConstHashEnumData::new),
    ConstHashTable(new ConstHashTableData().fillSampleData(), ConstHashTableData::new),
    ConstList(new ConstListData().fillSampleData(), ConstListData::new),
    CopyOnWriteArrayList(new CopyOnWriteArrayListData().fillSampleData(), CopyOnWriteArrayListData::new),
    CopyOnWriteArraySet(new CopyOnWriteArraySetData().fillSampleData(), CopyOnWriteArraySetData::new),
    Currency(new CurrencyData().fillSampleData(), CurrencyData::new),
    CustomEnum(new CustomEnumTrivialData().fillSampleData(), CustomEnumTrivialData::new),
    Date(new DateData().fillSampleData(), DateData::new),
    EqBulkList(new EqBulkListData().fillSampleData(), EqBulkListData::new),
    EqConstHashEnum(new EqConstHashEnumData().fillSampleData(), EqConstHashEnumData::new),
    EqConstHashTable(new EqConstHashTableData().fillSampleData(), EqConstHashTableData::new),
    EqHashEnum(new EqHashEnumData().fillSampleData(), EqHashEnumData::new),
    File(new FileData().fillSampleData(), FileData::new),
    HashEnum(new HashEnumData().fillSampleData(), HashEnumData::new),
    HashMap(new HashMapData().fillSampleData(), HashMapData::new),
    HashSet(new HashSetData().fillSampleData(), HashSetData::new),
    HashTable(new HashtableData().fillSampleData(), HashtableData::new),
    HashTableMicroStream(new HashTableMSData().fillSampleData(), HashTableMSData::new),
    //IdentityHashMap(new IdentityHashMapData().fillSampleData(), IdentityHashMapData::new),
    InetAddress(new IntetAddressData().fillSampleData(), IntetAddressData::new),
    LimitList(new LimitListData().fillSampleData(), LimitListData::new),
    LinkedHashMap(new LinkedHashMapData().fillSampleData(), LinkedHashMapData::new),
    LinkedHashSet(new LinkedHashSetData().fillSampleData(), LinkedHashSetData::new),
    LinkedList(new LinkedListData().fillSampleData(), LinkedListData::new),
    OptionalDouble(new OptionalDoubleData().fillSampleData(), OptionalDoubleData::new),
    OptionalInt(new OptionalIntData().fillSampleData(), OptionalIntData::new),
    OptionalLong(new OptionalLongData().fillSampleData(), OptionalLongData::new),
    Path(new PathData().fillSampleData(), PathData::new),
    PersistenceRootsDefault(new RootsData().fillSampleData(), RootsData::new),
    PriorityQueue(new PriorityQueueData().fillSampleData(), PriorityQueueData::new),
    Properties(new PropertiesData().fillSampleData(), PropertiesData::new),
    RegexPattern(new RegexPatternData().fillSampleData(), RegexPatternData::new),
    SocketAddress(new SocketAddressData().fillSampleData(), SocketAddressData::new),
    Stack(new StackData().fillSampleData(), StackData::new),
    StringBuffer(new StringBufferData().fillSampleData(), StringBufferData::new),
    StringBuilder(new StringBuilderData().fillSampleData(), StringBuilderData::new),
    Substituter(new SubstituterDefaultData().fillSampleData(), SubstituterDefaultData::new),
    TreeMap(new TreeMapData().fillSampleData(), TreeMapData::new),
    TreeSet(new TreeSetData().fillSampleData(), TreeSetData::new),
    Vector(new VectorData().fillSampleData(), VectorData::new),
    WeakHashMap(new WeakHashMapData().fillSampleData(), WeakHashMapData::new),
    URI(new URIData().fillSampleData(), URIData::new),
    URL(new JavaNetURLData().fillSampleData(), JavaNetURLData::new),
    UUID(new UUIDData().fillSampleData(), UUIDData::new),
    PrimitiveArrayTypes(new PrimitiveArrayTypes().fillSampleData(), PrimitiveArrayTypes::new),
    Java_util_Calendar(new CalendarApiData().fillSampleData(), CalendarApiData::new),
    Java_SQL_Dates(new SqlCalendarData().fillSampleData(), SqlCalendarData::new),
    Atomic_types(new AtomicPrimitiveData().fillSampleData(), AtomicPrimitiveData::new),
    BitSet(new BitSetData().fillSampleData(), BitSetData::new);

    final private BinaryHandlerTestData original;
    final private Supplier<?> instanceSupplier;

    TypeEnumCellular(BinaryHandlerTestData original, Supplier<?> copy) {
        this.original = original;
        this.instanceSupplier = copy;
    }

    public BinaryHandlerTestData getOriginal() {
        return original;
    }

    public Object createEmptyInstance() {
        return instanceSupplier.get();
    }

    @Deprecated
    public Supplier<?> getCopy() {
        return instanceSupplier;
    }
}
