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

/**
 * Simple placeholder for all those extra fields we don't want to deal
 * with.
 *
 * <p>Assumes local file data and central directory entries are
 * identical - unless told the opposite.</p>
 *
 */
public class _ApacheUnrecognizedExtraField
    implements _ApacheCentralDirectoryParsingZipExtraField {

    /**
     * The Header-ID.
     *
     * @since 1.1
     */
    private _ApacheZipShort headerId;

    /**
     * Set the header id.
     * @param headerId the header id to use
     */
    public void setHeaderId(_ApacheZipShort headerId) {
        this.headerId = headerId;
    }

    /**
     * Get the header id.
     * @return the header id
     */
    public _ApacheZipShort getHeaderId() {
        return headerId;
    }

    /**
     * Extra field data in local file data - without
     * Header-ID or length specifier.
     *
     * @since 1.1
     */
    private byte[] localData;

    /**
     * Set the extra field data in the local file data -
     * without Header-ID or length specifier.
     * @param data the field data to use
     */
    public void setLocalFileDataData(byte[] data) {
        localData = _ApacheZipUtil.copy(data);
    }

    /**
     * Get the length of the local data.
     * @return the length of the local data
     */
    public _ApacheZipShort getLocalFileDataLength() {
        return new _ApacheZipShort(localData.length);
    }

    /**
     * Get the local data.
     * @return the local data
     */
    public byte[] getLocalFileDataData() {
        return _ApacheZipUtil.copy(localData);
    }

    /**
     * Extra field data in central directory - without
     * Header-ID or length specifier.
     *
     * @since 1.1
     */
    private byte[] centralData;

    /**
     * Set the extra field data in central directory.
     * @param data the data to use
     */
    public void setCentralDirectoryData(byte[] data) {
        centralData = _ApacheZipUtil.copy(data);
    }

    /**
     * Get the central data length.
     * If there is no central data, get the local file data length.
     * @return the central data length
     */
    public _ApacheZipShort getCentralDirectoryLength() {
        if (centralData != null) {
            return new _ApacheZipShort(centralData.length);
        }
        return getLocalFileDataLength();
    }

    /**
     * Get the central data.
     * @return the central data if present, else return the local file data
     */
    public byte[] getCentralDirectoryData() {
        if (centralData != null) {
            return _ApacheZipUtil.copy(centralData);
        }
        return getLocalFileDataData();
    }

    /**
     * @param data the array of bytes.
     * @param offset the source location in the data array.
     * @param length the number of bytes to use in the data array.
     * @see _ApacheZipExtraField#parseFromLocalFileData(byte[], int, int)
     */
    public void parseFromLocalFileData(byte[] data, int offset, int length) {
        byte[] tmp = new byte[length];
        System.arraycopy(data, offset, tmp, 0, length);
        setLocalFileDataData(tmp);
    }

    /**
     * @param data the array of bytes.
     * @param offset the source location in the data array.
     * @param length the number of bytes to use in the data array.
     */
    public void parseFromCentralDirectoryData(byte[] data, int offset,
                                              int length) {
        byte[] tmp = new byte[length];
        System.arraycopy(data, offset, tmp, 0, length);
        setCentralDirectoryData(tmp);
        if (localData == null) {
            setLocalFileDataData(tmp);
        }
    }

}
