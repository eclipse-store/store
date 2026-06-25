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

import org.opentest4j.MultipleFailuresError;
import test.eclipse.store.library.types.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class TypeRegister {
    final private PrimitiveTypes primitiveTypes = new PrimitiveTypes();
    final private ArrayDequeData arrayDeque = new ArrayDequeData();
    final private ArrayListData arrayListData = new ArrayListData();
    final private BasicNonPrimitiveArrayTypes basicNonPrimitiveArrayTypes = new BasicNonPrimitiveArrayTypes();
    final private BasicNonPrimitive basicNonPrimitive = new BasicNonPrimitive();
    final private BigDecimalData bigDecimalData = new BigDecimalData();
    final private BigIntegerData bigIntegerData = new BigIntegerData();
    final private BitSetData bitSetData = new BitSetData();
    final private BulkListData bulkListData = new BulkListData();
    final private ClassTypeData classTypeData = new ClassTypeData();
    final private ConcurrentHashMapData concurrentHashMapData = new ConcurrentHashMapData();
    final private ConcurrentLinkedDequeData concurrentLinkedDequeData = new ConcurrentLinkedDequeData();
    final private ConcurrentLinkedQueueData concurrentLinkedQueueData = new ConcurrentLinkedQueueData();
    final private ConcurrentSkipListMapData concurrentSkipListMapData = new ConcurrentSkipListMapData();
    final private ConcurrentSkipListSetData concurrentSkipListSetData = new ConcurrentSkipListSetData();
    final private ConstHashEnumData constHashEnumData = new ConstHashEnumData();
    final private ConstHashTableData constHashTableData = new ConstHashTableData();
    final private ConstListData constListData = new ConstListData();
    final private CopyOnWriteArrayListData copyOnWriteArrayListData = new CopyOnWriteArrayListData();
    final private CurrencyData currencyData = new CurrencyData();
    final private CustomEnumTrivialData customEnumTrivialData = new CustomEnumTrivialData();
    final private DateData dateData = new DateData();
    final private EqBulkListData eqBulkListData = new EqBulkListData();
    final private EqConstHashEnumData eqConstHashEnumData = new EqConstHashEnumData();
    final private EqConstHashTableData eqConstHashTableData = new EqConstHashTableData();
    final private EqHashEnumData eqHashEnumData = new EqHashEnumData();
    final private FileData fileData = new FileData();
    final private HashEnumData hashEnumData = new HashEnumData();
    final private HashMapData hashMapData = new HashMapData();
    final private HashSetData hashSetData = new HashSetData();
    final private HashtableData hashtableData = new HashtableData();
    final private HashTableMSData hashTableMSData = new HashTableMSData();
    final private IdentityHashMapData identityHashMapData = new IdentityHashMapData();
    final private IntetAddressData intetAddressData = new IntetAddressData();
    final private LazyData lazyData = new LazyData();
    final private LimitListData limitListData = new LimitListData();
    final private LinkedHashMapData linkedHashMapData = new LinkedHashMapData();
    final private LinkedHashSetData linkedHashSetData = new LinkedHashSetData();
    final private LinkedListData linkedListData = new LinkedListData();
    final private OptionalDoubleData optionalDoubleData = new OptionalDoubleData();
    final private OptionalIntData optionalIntData = new OptionalIntData();
    final private OptionalLongData optionalLongData = new OptionalLongData();
    final private PathData pathData = new PathData();
    final private RootsData rootsData = new RootsData();
    final private PriorityQueueData priorityQueueData = new PriorityQueueData();
    final private PropertiesData propertiesData = new PropertiesData();
    final private RegexPatternData regexPatternData = new RegexPatternData();
    final private SocketAddressData socketAddressData = new SocketAddressData();
    final private StackData stackData = new StackData();
    final private StringBufferData stringBufferData = new StringBufferData();
    final private StringBuilderData stringBuilderData = new StringBuilderData();
    final private SubstituterDefaultData substituterDefaultData = new SubstituterDefaultData();
    final private TreeMapData treeMapData = new TreeMapData();
    final private TreeSetData treeSetData = new TreeSetData();
    final private VectorData vectorData = new VectorData();
    final private WeakHashMapData weakHashMapData = new WeakHashMapData();
    final private URIData uriData = new URIData();
    final private JavaNetURLData javaNetURLData = new JavaNetURLData();
    final private UUIDData uuidData = new UUIDData();
    final private PrimitiveArrayTypes primitiveArrayTypes = new PrimitiveArrayTypes();
    final private CalendarApiData calendarApiData = new CalendarApiData();
    final private SqlCalendarData sqlCalendarData = new SqlCalendarData();
    final private AtomicPrimitiveData atomicPrimitiveData = new AtomicPrimitiveData();
    final private ThrowableData throwableData = new ThrowableData();
    final private LocalDateTimeData localDateTimeData = new LocalDateTimeData();
    final private LocalDateData localDateData = new LocalDateData();


    public TypeRegister fillSampleDate() {
        Field[] fields = this.getClass().getDeclaredFields();
        Arrays.asList(fields).forEach(f -> {
            f.setAccessible(true);
            try {
                Object data = f.get(this);
                Method fillMethod = data.getClass().getDeclaredMethod("fillSampleData");
                fillMethod.invoke(data);
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                e.printStackTrace();
            }
        });
        return this;
    }

    public Object getFieldsByName(String name) {
        Field[] fields = this.getClass().getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            if (field.getName().equals(name)) {
                try {
                    return field.get(this);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        throw new IllegalArgumentException(name);
    }

    public void proveData(TypeRegister copy, String... ignore) {
        List<Throwable> exceptionList = new ArrayList<>();
        Field[] fields = this.getClass().getDeclaredFields();
        List<String> ignoreList = Arrays.asList(ignore);
        for (Field f : fields){
            if (ignoreList.contains(f.getName()) ) {
                continue;
            }
            f.setAccessible(true);
            try {
                Object data = f.get(this);
                Field field = copy.getClass().getDeclaredField(f.getName());
                Object copyData = field.get(copy);
                Method fillMethod = data.getClass().getDeclaredMethod("proveResults", Object.class);
                fillMethod.invoke(data, copyData);
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | NoSuchFieldException e) {
                exceptionList.add(e);
                exceptionList.add(new RuntimeException(String.valueOf(f)));
            }
        }

        if (!exceptionList.isEmpty()) {
            MultipleFailuresError multipleFailuresError = new MultipleFailuresError("TypeRegister prove data: ", exceptionList);
            Objects.requireNonNull(multipleFailuresError);
            exceptionList.forEach(multipleFailuresError::addSuppressed);
            throw multipleFailuresError;
        }
    }

    public void proveData(TypeRegister copy) {
        proveData(copy, new String[]{});
    }


    public PrimitiveTypes getPrimitiveTypes() {
        return primitiveTypes;
    }

    public ArrayDequeData getArrayDeque() {
        return arrayDeque;
    }

    public ArrayListData getArrayListData() {
        return arrayListData;
    }

    public BasicNonPrimitiveArrayTypes getBasicNonPrimitiveArrayTypes() {
        return basicNonPrimitiveArrayTypes;
    }

    public BasicNonPrimitive getBasicNonPrimitive() {
        return basicNonPrimitive;
    }

    public BigDecimalData getBigDecimalData() {
        return bigDecimalData;
    }

    public BigIntegerData getBigIntegerData() {
        return bigIntegerData;
    }

    public BulkListData getBulkListData() {
        return bulkListData;
    }

    public ClassTypeData getClassTypeData() {
        return classTypeData;
    }

    public ConcurrentHashMapData getConcurrentHashMapData() {
        return concurrentHashMapData;
    }

    public ConcurrentLinkedDequeData getConcurrentLinkedDequeData() {
        return concurrentLinkedDequeData;
    }

    public ConcurrentLinkedQueueData getConcurrentLinkedQueueData() {
        return concurrentLinkedQueueData;
    }

    public ConcurrentSkipListMapData getConcurrentSkipListMapData() {
        return concurrentSkipListMapData;
    }

    public ConcurrentSkipListSetData getConcurrentSkipListSetData() {
        return concurrentSkipListSetData;
    }

    public ConstHashEnumData getConstHashEnumData() {
        return constHashEnumData;
    }

    public ConstHashTableData getConstHashTableData() {
        return constHashTableData;
    }

    public ConstListData getConstListData() {
        return constListData;
    }

    public CopyOnWriteArrayListData getCopyOnWriteArrayListData() {
        return copyOnWriteArrayListData;
    }

    public CurrencyData getCurrencyData() {
        return currencyData;
    }

    public CustomEnumTrivialData getCustomEnumTrivialData() {
        return customEnumTrivialData;
    }

    public DateData getDateData() {
        return dateData;
    }

    public EqBulkListData getEqBulkListData() {
        return eqBulkListData;
    }

    public EqConstHashEnumData getEqConstHashEnumData() {
        return eqConstHashEnumData;
    }

    public EqConstHashTableData getEqConstHashTableData() {
        return eqConstHashTableData;
    }

    public EqHashEnumData getEqHashEnumData() {
        return eqHashEnumData;
    }

    public FileData getFileData() {
        return fileData;
    }

    public HashEnumData getHashEnumData() {
        return hashEnumData;
    }

    public HashMapData getHashMapData() {
        return hashMapData;
    }

    public HashSetData getHashSetData() {
        return hashSetData;
    }

    public HashtableData getHashtableData() {
        return hashtableData;
    }

    public HashTableMSData getHashTableMSData() {
        return hashTableMSData;
    }

    public IdentityHashMapData getIdentityHashMapData() {
        return identityHashMapData;
    }

    public IntetAddressData getIntetAddressData() {
        return intetAddressData;
    }

    public LazyData getLazyData() {
        return lazyData;
    }

    public LimitListData getLimitListData() {
        return limitListData;
    }

    public LinkedHashMapData getLinkedHashMapData() {
        return linkedHashMapData;
    }

    public LinkedHashSetData getLinkedHashSetData() {
        return linkedHashSetData;
    }

    public LinkedListData getLinkedListData() {
        return linkedListData;
    }

    public OptionalDoubleData getOptionalDoubleData() {
        return optionalDoubleData;
    }

    public OptionalIntData getOptionalIntData() {
        return optionalIntData;
    }

    public OptionalLongData getOptionalLongData() {
        return optionalLongData;
    }

    public PathData getPathData() {
        return pathData;
    }

    public RootsData getRootsData() {
        return rootsData;
    }

    public PriorityQueueData getPriorityQueueData() {
        return priorityQueueData;
    }

    public PropertiesData getPropertiesData() {
        return propertiesData;
    }

    public RegexPatternData getRegexPatternData() {
        return regexPatternData;
    }

    public SocketAddressData getSocketAddressData() {
        return socketAddressData;
    }

    public StackData getStackData() {
        return stackData;
    }

    public StringBufferData getStringBufferData() {
        return stringBufferData;
    }

    public StringBuilderData getStringBuilderData() {
        return stringBuilderData;
    }

    public SubstituterDefaultData getSubstituterDefaultData() {
        return substituterDefaultData;
    }

    public TreeMapData getTreeMapData() {
        return treeMapData;
    }

    public TreeSetData getTreeSetData() {
        return treeSetData;
    }

    public VectorData getVectorData() {
        return vectorData;
    }

    public WeakHashMapData getWeakHashMapData() {
        return weakHashMapData;
    }

    public URIData getUriData() {
        return uriData;
    }

    public JavaNetURLData getJavaNetURLData() {
        return javaNetURLData;
    }

    public UUIDData getUuidData() {
        return uuidData;
    }

    public PrimitiveArrayTypes getPrimitiveArrayTypes() {
        return primitiveArrayTypes;
    }

    public ThrowableData getThrowableData()
    {
        return throwableData;
    }

    public CalendarApiData getCalendarApiData() {
        return calendarApiData;
    }

    public SqlCalendarData getSqlCalendarData() {
        return sqlCalendarData;
    }

    public AtomicPrimitiveData getAtomicPrimitiveData() {
        return atomicPrimitiveData;
    }

    public LocalDateTimeData getLocalDateTimeData()
    {
        return localDateTimeData;
    }

    @Override
    public String toString()
    {
        return "TypeRegister{" +
                "primitiveTypes=" + primitiveTypes +
                ", arrayDeque=" + arrayDeque +
                ", arrayListData=" + arrayListData +
                ", basicNonPrimitiveArrayTypes=" + basicNonPrimitiveArrayTypes +
                ", basicNonPrimitive=" + basicNonPrimitive +
                ", bigDecimalData=" + bigDecimalData +
                ", bigIntegerData=" + bigIntegerData +
                ", bulkListData=" + bulkListData +
                ", classTypeData=" + classTypeData +
                ", concurrentHashMapData=" + concurrentHashMapData +
                ", concurrentLinkedDequeData=" + concurrentLinkedDequeData +
                ", concurrentLinkedQueueData=" + concurrentLinkedQueueData +
                ", concurrentSkipListMapData=" + concurrentSkipListMapData +
                ", concurrentSkipListSetData=" + concurrentSkipListSetData +
                ", constHashEnumData=" + constHashEnumData +
                ", constHashTableData=" + constHashTableData +
                ", constListData=" + constListData +
                ", copyOnWriteArrayListData=" + copyOnWriteArrayListData +
                ", currencyData=" + currencyData +
                ", customEnumTrivialData=" + customEnumTrivialData +
                ", dateData=" + dateData +
                ", eqBulkListData=" + eqBulkListData +
                ", eqConstHashEnumData=" + eqConstHashEnumData +
                ", eqConstHashTableData=" + eqConstHashTableData +
                ", eqHashEnumData=" + eqHashEnumData +
                ", fileData=" + fileData +
                ", hashEnumData=" + hashEnumData +
                ", hashMapData=" + hashMapData +
                ", hashSetData=" + hashSetData +
                ", hashtableData=" + hashtableData +
                ", hashTableMSData=" + hashTableMSData +
                ", identityHashMapData=" + identityHashMapData +
                ", intetAddressData=" + intetAddressData +
                ", lazyData=" + lazyData +
                ", limitListData=" + limitListData +
                ", linkedHashMapData=" + linkedHashMapData +
                ", linkedHashSetData=" + linkedHashSetData +
                ", linkedListData=" + linkedListData +
                ", optionalDoubleData=" + optionalDoubleData +
                ", optionalIntData=" + optionalIntData +
                ", optionalLongData=" + optionalLongData +
                ", pathData=" + pathData +
                ", rootsData=" + rootsData +
                ", priorityQueueData=" + priorityQueueData +
                ", propertiesData=" + propertiesData +
                ", regexPatternData=" + regexPatternData +
                ", socketAddressData=" + socketAddressData +
                ", stackData=" + stackData +
                ", stringBufferData=" + stringBufferData +
                ", stringBuilderData=" + stringBuilderData +
                ", substituterDefaultData=" + substituterDefaultData +
                ", treeMapData=" + treeMapData +
                ", treeSetData=" + treeSetData +
                ", vectorData=" + vectorData +
                ", weakHashMapData=" + weakHashMapData +
                ", uriData=" + uriData +
                ", javaNetURLData=" + javaNetURLData +
                ", uuidData=" + uuidData +
                ", primitiveArrayTypes=" + primitiveArrayTypes +
                ", calendarApiData=" + calendarApiData +
                ", sqlCalendarData=" + sqlCalendarData +
                ", atomicPrimitiveData=" + atomicPrimitiveData +
                ", throwableData=" + throwableData +
                ", localDateTimeData=" + localDateTimeData +
                '}';
    }
}
