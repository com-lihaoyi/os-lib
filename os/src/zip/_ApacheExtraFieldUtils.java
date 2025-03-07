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

package os;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipException;

/**
 * _ApacheZipExtraField related methods
 *
 */
// CheckStyle:HideUtilityClassConstructorCheck OFF (bc)
public class _ApacheExtraFieldUtils {

    private static final int WORD = 4;

    /**
     * Static registry of known extra fields.
     *
     * @since 1.1
     */
    private static final Map<_ApacheZipShort, Class<?>> implementations;

    static {
        implementations = new ConcurrentHashMap<>();
        register(_ApacheAsiExtraField.class);
        register(_ApacheJarMarker.class);
        register(_ApacheUnicodePathExtraField.class);
        register(_ApacheUnicodeCommentExtraField.class);
        register(_ApacheZip64ExtendedInformationExtraField.class);
    }

    /**
     * Register a _ApacheZipExtraField implementation.
     *
     * <p>The given class must have a no-arg constructor and implement
     * the {@link _ApacheZipExtraField _ApacheZipExtraField interface}.</p>
     * @param c the class to register
     *
     * @since 1.1
     */
    public static void register(Class<?> c) {
        try {
            _ApacheZipExtraField ze = (_ApacheZipExtraField) c.getDeclaredConstructor().newInstance();
            implementations.put(ze.getHeaderId(), c);
        } catch (ClassCastException cc) {
            throw new RuntimeException(c + " doesn't implement _ApacheZipExtraField"); //NOSONAR
        } catch (InstantiationException ie) {
            throw new RuntimeException(c + " is not a concrete class"); //NOSONAR
        } catch (IllegalAccessException ie) {
            throw new RuntimeException(c + "'s no-arg constructor is not public"); //NOSONAR
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(c + "'s no-arg constructor not found"); //NOSONAR
        } catch (InvocationTargetException e) {
            throw new RuntimeException(c + "'s no-arg constructor threw an exception:"
                    + e.getMessage()); //NOSONAR
        }
    }

    /**
     * Create an instance of the appropriate ExtraField, falls back to
     * {@link _ApacheUnrecognizedExtraField _ApacheUnrecognizedExtraField}.
     * @param headerId the header identifier
     * @return an instance of the appropriate ExtraField
     * @exception InstantiationException if unable to instantiate the class
     * @exception IllegalAccessException if not allowed to instantiate the class
     * @since 1.1
     */
    public static _ApacheZipExtraField createExtraField(_ApacheZipShort headerId)
        throws InstantiationException, IllegalAccessException {
        Class<?> c = implementations.get(headerId);
        if (c != null) {
            // wrap extra exceptions to preserve method signature
            try {
                return (_ApacheZipExtraField) c.getDeclaredConstructor().newInstance();
            } catch (InvocationTargetException e) {
                throw (InstantiationException)
                        new InstantiationException().initCause(e.getTargetException());
            } catch (NoSuchMethodException e) {
                throw (InstantiationException)
                        new InstantiationException().initCause(e);
            }
        }
        _ApacheUnrecognizedExtraField u = new _ApacheUnrecognizedExtraField();
        u.setHeaderId(headerId);
        return u;
    }

    /**
     * Split the array into ExtraFields and populate them with the
     * given data as local file data, throwing an exception if the
     * data cannot be parsed.
     * @param data an array of bytes as it appears in local file data
     * @return an array of ExtraFields
     * @throws ZipException on error
     */
    public static _ApacheZipExtraField[] parse(byte[] data) throws ZipException {
        return parse(data, true, UnparseableExtraField.THROW);
    }

    /**
     * Split the array into ExtraFields and populate them with the
     * given data, throwing an exception if the data cannot be parsed.
     * @param data an array of bytes
     * @param local whether data originates from the local file data
     * or the central directory
     * @return an array of ExtraFields
     * @since 1.1
     * @throws ZipException on error
     */
    public static _ApacheZipExtraField[] parse(byte[] data, boolean local)
        throws ZipException {
        return parse(data, local, UnparseableExtraField.THROW);
    }

    /**
     * Split the array into ExtraFields and populate them with the
     * given data.
     * @param data an array of bytes
     * @param local whether data originates from the local file data
     * or the central directory
     * @param onUnparseableData what to do if the extra field data
     * cannot be parsed.
     * @return an array of ExtraFields
     * @throws ZipException on error
     * @since Ant 1.8.1
     */
    public static _ApacheZipExtraField[] parse(byte[] data, boolean local,
                                        UnparseableExtraField onUnparseableData)
        throws ZipException {
        List<_ApacheZipExtraField> v = new ArrayList<>();
        int start = 0;
        LOOP:
        while (start <= data.length - WORD) {
            _ApacheZipShort headerId = new _ApacheZipShort(data, start);
            int length = (new _ApacheZipShort(data, start + 2)).getValue();
            if (start + WORD + length > data.length) {
                switch (onUnparseableData.getKey()) {
                    case UnparseableExtraField.THROW_KEY:
                        throw new ZipException("bad extra field starting at "
                                + start + ".  Block length of " + length
                                + " bytes exceeds remaining data of "
                                + (data.length - start - WORD) + " bytes.");
                    case UnparseableExtraField.READ_KEY:
                        _ApacheUnparseableExtraFieldData field = new _ApacheUnparseableExtraFieldData();
                        if (local) {
                            field.parseFromLocalFileData(data, start, data.length - start);
                        } else {
                            field.parseFromCentralDirectoryData(data, start, data.length - start);
                        }
                        v.add(field);
                        //$FALL-THROUGH$
                    case UnparseableExtraField.SKIP_KEY:
                        // since we cannot parse the data we must assume
                        // the extra field consumes the whole rest of the
                        // available data
                        break LOOP;
                    default:
                        throw new ZipException("unknown UnparseableExtraField key: "
                                + onUnparseableData.getKey());
                }
            }
            try {
                _ApacheZipExtraField ze = createExtraField(headerId);
                if (local || !(ze instanceof _ApacheCentralDirectoryParsingZipExtraField)) {
                    ze.parseFromLocalFileData(data, start + WORD, length);
                } else {
                    ((_ApacheCentralDirectoryParsingZipExtraField) ze)
                        .parseFromCentralDirectoryData(data, start + WORD, length);
                }
                v.add(ze);
            } catch (InstantiationException | IllegalAccessException ie) {
                throw new ZipException(ie.getMessage());
            }
            start += (length + WORD);
        }

        _ApacheZipExtraField[] result = new _ApacheZipExtraField[v.size()];
        return v.toArray(result);
    }

    /**
     * Merges the local file data fields of the given ZipExtraFields.
     * @param data an array of ExtraFiles
     * @return an array of bytes
     * @since 1.1
     */
    public static byte[] mergeLocalFileDataData(_ApacheZipExtraField[] data) {
        final boolean lastIsUnparseableHolder = data.length > 0
                && data[data.length - 1] instanceof _ApacheUnparseableExtraFieldData;
        int regularExtraFieldCount = lastIsUnparseableHolder ? data.length - 1 : data.length;

        int sum = WORD * regularExtraFieldCount;
        for (_ApacheZipExtraField element : data) {
            sum += element.getLocalFileDataLength().getValue();
        }

        byte[] result = new byte[sum];
        int start = 0;
        for (int i = 0; i < regularExtraFieldCount; i++) {
            System.arraycopy(data[i].getHeaderId().getBytes(),
                    0, result, start, 2);
            System.arraycopy(data[i].getLocalFileDataLength().getBytes(),
                    0, result, start + 2, 2);
            byte[] local = data[i].getLocalFileDataData();
            System.arraycopy(local, 0, result, start + WORD, local.length);
            start += (local.length + WORD);
        }
        if (lastIsUnparseableHolder) {
            byte[] local = data[data.length - 1].getLocalFileDataData();
            System.arraycopy(local, 0, result, start, local.length);
        }
        return result;
    }

    /**
     * Merges the central directory fields of the given ZipExtraFields.
     * @param data an array of ExtraFields
     * @return an array of bytes
     * @since 1.1
     */
    public static byte[] mergeCentralDirectoryData(_ApacheZipExtraField[] data) {
        final boolean lastIsUnparseableHolder = data.length > 0
            && data[data.length - 1] instanceof _ApacheUnparseableExtraFieldData;
        int regularExtraFieldCount = lastIsUnparseableHolder ? data.length - 1 : data.length;

        int sum = WORD * regularExtraFieldCount;
        for (_ApacheZipExtraField element : data) {
            sum += element.getCentralDirectoryLength().getValue();
        }
        byte[] result = new byte[sum];
        int start = 0;
        for (int i = 0; i < regularExtraFieldCount; i++) {
            System.arraycopy(data[i].getHeaderId().getBytes(),
                             0, result, start, 2);
            System.arraycopy(data[i].getCentralDirectoryLength().getBytes(),
                             0, result, start + 2, 2);
            byte[] local = data[i].getCentralDirectoryData();
            System.arraycopy(local, 0, result, start + WORD, local.length);
            start += (local.length + WORD);
        }
        if (lastIsUnparseableHolder) {
            byte[] local = data[data.length - 1].getCentralDirectoryData();
            System.arraycopy(local, 0, result, start, local.length);
        }
        return result;
    }

    /**
     * "enum" for the possible actions to take if the extra field
     * cannot be parsed.
     */
    public static final class UnparseableExtraField {
        /**
         * Key for "throw an exception" action.
         */
        public static final int THROW_KEY = 0;
        /**
         * Key for "skip" action.
         */
        public static final int SKIP_KEY = 1;
        /**
         * Key for "read" action.
         */
        public static final int READ_KEY = 2;

        /**
         * Throw an exception if field cannot be parsed.
         */
        public static final UnparseableExtraField THROW = new UnparseableExtraField(THROW_KEY);

        /**
         * Skip the extra field entirely and don't make its data
         * available - effectively removing the extra field data.
         */
        public static final UnparseableExtraField SKIP = new UnparseableExtraField(SKIP_KEY);

        /**
         * Read the extra field data into an instance of {@link
         * _ApacheUnparseableExtraFieldData _ApacheUnparseableExtraFieldData}.
         */
        public static final UnparseableExtraField READ = new UnparseableExtraField(READ_KEY);

        private final int key;

        private UnparseableExtraField(int k) {
            key = k;
        }

        /**
         * Key of the action to take.
         *
         * @return int
         */
        public int getKey() {
            return key;
        }
    }
}
