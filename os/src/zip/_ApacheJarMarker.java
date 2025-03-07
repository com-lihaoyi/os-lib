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

import java.util.zip.ZipException;

/**
 * If this extra field is added as the very first extra field of the
 * archive, Solaris will consider it an executable jar file.
 *
 * @since Ant 1.6.3
 */
public final class _ApacheJarMarker implements _ApacheZipExtraField {

    private static final _ApacheZipShort ID = new _ApacheZipShort(0xCAFE);
    private static final _ApacheZipShort NULL = new _ApacheZipShort(0);
    private static final byte[] NO_BYTES = new byte[0];
    private static final _ApacheJarMarker DEFAULT = new _ApacheJarMarker();

    /** No-arg constructor */
    public _ApacheJarMarker() {
        // empty
    }

    /**
     * Since _ApacheJarMarker is stateless we can always use the same instance.
     * @return the DEFAULT jarmaker.
     */
    public static _ApacheJarMarker getInstance() {
        return DEFAULT;
    }

    /**
     * The Header-ID.
     * @return the header id
     */
    public _ApacheZipShort getHeaderId() {
        return ID;
    }

    /**
     * Length of the extra field in the local file data - without
     * Header-ID or length specifier.
     * @return 0
     */
    public _ApacheZipShort getLocalFileDataLength() {
        return NULL;
    }

    /**
     * Length of the extra field in the central directory - without
     * Header-ID or length specifier.
     * @return 0
     */
    public _ApacheZipShort getCentralDirectoryLength() {
        return NULL;
    }

    /**
     * The actual data to put into local file data - without Header-ID
     * or length specifier.
     * @return the data
     * @since 1.1
     */
    public byte[] getLocalFileDataData() {
        return NO_BYTES;
    }

    /**
     * The actual data to put central directory - without Header-ID or
     * length specifier.
     * @return the data
     */
    public byte[] getCentralDirectoryData() {
        return NO_BYTES;
    }

    /**
     * Populate data from this array as if it was in local file data.
     * @param data an array of bytes
     * @param offset the start offset
     * @param length the number of bytes in the array from offset
     *
     * @throws ZipException on error
     */
    public void parseFromLocalFileData(byte[] data, int offset, int length)
        throws ZipException {
        if (length != 0) {
            throw new ZipException("_ApacheJarMarker doesn't expect any data");
        }
    }
}
