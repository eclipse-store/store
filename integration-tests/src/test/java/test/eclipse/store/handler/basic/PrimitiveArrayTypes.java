package test.eclipse.store.handler.basic;

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

import test.eclipse.store.handler.BinaryHandlerTestData;

public class PrimitiveArrayTypes implements BinaryHandlerTestData {
    private static final byte SAMPLE_BYTE = 100;
    private static final byte SAMPLE_BYTE_2 = 127;
    private static final short SAMPLE_SHORT = 50;
    private static final short SAMPLE_SHORT_2 = 100;
    private static final int SAMPLE_INT = 5401;
    private static final int SAMPLE_INT_2 = 4401;
    private static final long SAMPLE_LONG = 1545455464654L;
    private static final long SAMPLE_LONG_2 = 25546455464654L;
    private static final float SAMPLE_FLOAT = 3.141526f;
    private static final float SAMPLE_FLOAT_2 = 2.141526f;
    private static final double SAMPLE_DOUBLE = 3.141526545;
    private static final double SAMPLE_DOUBLE_2 = 5.141526545;
    private static final char SAMPLE_CHAR = 'c';
    private static final char SAMPLE_CHAR_2 = 'x';

    private byte[] byteValues;
    private short[] shortValues;
    private int[] intValues;
    private long[] longValues;
    private float[] floatValues;
    private double[] doubleValues;
    private boolean[] booleanValues;
    private char[] charValues;
    private char[] charEmptyValues;

    private byte[][] byteValueMatrix;
    private short[][] shortValueMatrix;
    private int[][] intValueMatrix;
    private long[][] longValueMatrix;
    private float[][] floatValueMatrix;
    private double[][] doubleValueMatrix;
    private boolean[][] booleanValueMatrix;
    private char[][] charValueMatrix;
    private char[][] charEmptyMatrix;


    public static PrimitiveArrayTypes createEmpty() {
        PrimitiveArrayTypes a = new PrimitiveArrayTypes();
        a.byteValues = new byte[1];
        a.shortValues = new short[1];
        a.intValues = new int[1];
        a.longValues = new long[1];
        a.floatValues = new float[1];
        a.doubleValues = new double[1];
        a.booleanValues = new boolean[1];
        a.charValues = new char[1];
        a.charEmptyValues = new char[0];

        a.byteValueMatrix = new byte[1][1];
        a.shortValueMatrix = new short[1][1];
        a.intValueMatrix = new int[1][1];
        a.longValueMatrix = new long[1][1];
        a.floatValueMatrix = new float[1][1];
        a.doubleValueMatrix = new double[1][1];
        a.booleanValueMatrix = new boolean[1][1];
        a.charValueMatrix = new char[1][1];
        a.charEmptyMatrix = new char[0][0];
        return a;
    }

    @Override
    public void fillSampleData() {
        byteValues = new byte[]{SAMPLE_BYTE, SAMPLE_BYTE_2};
        shortValues = new short[]{SAMPLE_SHORT, SAMPLE_SHORT_2};
        intValues = new int[]{SAMPLE_INT, SAMPLE_INT_2};
        longValues = new long[]{SAMPLE_LONG, SAMPLE_LONG_2};
        floatValues = new float[]{SAMPLE_FLOAT, SAMPLE_FLOAT_2};
        doubleValues = new double[]{SAMPLE_DOUBLE, SAMPLE_DOUBLE_2};
        booleanValues = new boolean[]{Boolean.TRUE, Boolean.FALSE};
        charValues = new char[]{SAMPLE_CHAR, SAMPLE_CHAR_2};
        charEmptyValues = new char[0];

        byteValueMatrix = new byte[][]{{SAMPLE_BYTE, SAMPLE_BYTE_2}, {SAMPLE_BYTE, SAMPLE_BYTE_2}};
        shortValueMatrix = new short[][]{{SAMPLE_SHORT, SAMPLE_SHORT_2}, {SAMPLE_SHORT, SAMPLE_SHORT_2}};
        intValueMatrix = new int[][]{{SAMPLE_INT, SAMPLE_INT_2}, {SAMPLE_INT, SAMPLE_INT_2}};
        longValueMatrix = new long[][]{{SAMPLE_LONG, SAMPLE_LONG_2}, {SAMPLE_LONG, SAMPLE_LONG_2}};
        floatValueMatrix = new float[][]{{SAMPLE_FLOAT, SAMPLE_FLOAT_2}, {SAMPLE_FLOAT, SAMPLE_FLOAT_2}};
        doubleValueMatrix = new double[][]{{SAMPLE_DOUBLE, SAMPLE_DOUBLE_2}, {SAMPLE_DOUBLE, SAMPLE_DOUBLE_2}};
        booleanValueMatrix = new boolean[][]{{Boolean.TRUE, Boolean.FALSE}, {Boolean.TRUE, Boolean.FALSE}};
        charValueMatrix = new char[][]{{SAMPLE_CHAR, SAMPLE_CHAR_2}, {SAMPLE_CHAR, SAMPLE_CHAR_2}};
        charEmptyMatrix = new char[0][0];
    }

    public byte[] getByteValues() {
        return byteValues;
    }

    public short[] getShortValues() {
        return shortValues;
    }

    public int[] getIntValues() {
        return intValues;
    }

    public long[] getLongValues() {
        return longValues;
    }

    public float[] getFloatValues() {
        return floatValues;
    }

    public double[] getDoubleValues() {
        return doubleValues;
    }

    public boolean[] getBooleanValues() {
        return booleanValues;
    }

    public char[] getCharValues() {
        return charValues;
    }

    public byte[][] getByteValueMatrix() {
        return byteValueMatrix;
    }

    public short[][] getShortValueMatrix() {
        return shortValueMatrix;
    }

    public int[][] getIntValueMatrix() {
        return intValueMatrix;
    }

    public long[][] getLongValueMatrix() {
        return longValueMatrix;
    }

    public float[][] getFloatValueMatrix() {
        return floatValueMatrix;
    }

    public double[][] getDoubleValueMatrix() {
        return doubleValueMatrix;
    }

    public boolean[][] getBooleanValueMatrix() {
        return booleanValueMatrix;
    }

    public char[][] getCharValueMatrix() {
        return charValueMatrix;
    }

}
