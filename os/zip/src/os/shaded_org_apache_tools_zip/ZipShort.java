/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package os.shaded_org_apache_tools_zip;

import static os.shaded_org_apache_tools_zip.ZipConstants.BYTE_MASK;

/**
 * Utility class that represents a two byte integer with conversion
 * rules for the big endian byte order of ZIP files.
 *
 */
final class ZipShort implements Cloneable {
    private static final int BYTE_1_MASK = 0xFF00;
    private static final int BYTE_1_SHIFT = 8;

    private final int value;

    /**
     * Create instance from a number.
     * @param value the int to store as a ZipShort
     * @since 1.1
     */
    public ZipShort(int value) {
        this.value = value;
    }

    /**
     * Create instance from bytes.
     * @param bytes the bytes to store as a ZipShort
     * @since 1.1
     */
    public ZipShort(byte[] bytes) {
        this(bytes, 0);
    }

    /**
     * Create instance from the two bytes starting at offset.
     * @param bytes the bytes to store as a ZipShort
     * @param offset the offset to start
     * @since 1.1
     */
    public ZipShort(byte[] bytes, int offset) {
        value = ZipShort.getValue(bytes, offset);
    }

    /**
     * Get value as two bytes in big endian byte order.
     * @return the value as a a two byte array in big endian byte order
     * @since 1.1
     */
    public byte[] getBytes() {
        byte[] result = new byte[2];
        putShort(value, result, 0);
        return result;
    }

    /**
     * put the value as two bytes in big endian byte order.
     * @param value the Java int to convert to bytes
     * @param buf the output buffer
     * @param  offset
     *         The offset within the output buffer of the first byte to be written.
     *         must be non-negative and no larger than <code>buf.length-2</code>
     */
    public static void putShort(int value, byte[] buf, int offset) {
        buf[offset] = (byte) (value & BYTE_MASK);
        buf[offset + 1] = (byte) ((value & BYTE_1_MASK) >> BYTE_1_SHIFT);
    }

    /**
     * Get value as Java int.
     * @return value as a Java int
     * @since 1.1
     */
    public int getValue() {
        return value;
    }

    /**
     * Get value as two bytes in big endian byte order.
     * @param value the Java int to convert to bytes
     * @return the converted int as a byte array in big endian byte order
     */
    public static byte[] getBytes(int value) {
        byte[] result = new byte[2];
        result[0] = (byte) (value & BYTE_MASK);
        result[1] = (byte) ((value & BYTE_1_MASK) >> BYTE_1_SHIFT);
        return result;
    }

    /**
     * Helper method to get the value as a java int from two bytes starting at given array offset
     * @param bytes the array of bytes
     * @param offset the offset to start
     * @return the corresponding java int value
     */
    public static int getValue(byte[] bytes, int offset) {
        int value = (bytes[offset + 1] << BYTE_1_SHIFT) & BYTE_1_MASK;
        value += (bytes[offset] & BYTE_MASK);
        return value;
    }

    /**
     * Helper method to get the value as a java int from a two-byte array
     * @param bytes the array of bytes
     * @return the corresponding java int value
     */
    public static int getValue(byte[] bytes) {
        return getValue(bytes, 0);
    }

    /**
     * Override to make two instances with same value equal.
     * @param o an object to compare
     * @return true if the objects are equal
     * @since 1.1
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof ZipShort && value == ((ZipShort) o).getValue();
    }

    /**
     * Override to make two instances with same value equal.
     * @return the value stored in the ZipShort
     * @since 1.1
     */
    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException cnfe) {
            // impossible
            throw new RuntimeException(cnfe); //NOSONAR
        }
    }

    @Override
    public String toString() {
        return "ZipShort value: " + value;
    }
}
