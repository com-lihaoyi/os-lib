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
 * Parser/encoder for the "general purpose bit" field in ZIP's local
 * file and central directory headers.
 *
 * @since Ant 1.9.0
 */
public final class _ApacheGeneralPurposeBit implements Cloneable {
    /**
     * Indicates that the file is encrypted.
     */
    private static final int ENCRYPTION_FLAG = 1;

    /**
     * Indicates that a data descriptor stored after the file contents
     * will hold CRC and size information.
     */
    private static final int DATA_DESCRIPTOR_FLAG = 1 << 3;

    /**
     * Indicates strong encryption.
     */
    private static final int STRONG_ENCRYPTION_FLAG = 1 << 6;

    /**
     * Indicates that filenames are written in utf-8.
     *
     * <p>The only reason this is public is that {@link
     * _ApacheZipOutputStream#EFS_FLAG} was public in several versions of
     * Apache Ant and we needed a substitute for it.</p>
     */
    public static final int UFT8_NAMES_FLAG = 1 << 11;

    private boolean languageEncodingFlag = false;
    private boolean dataDescriptorFlag = false;
    private boolean encryptionFlag = false;
    private boolean strongEncryptionFlag = false;

    public _ApacheGeneralPurposeBit() {
    }

    /**
     * whether the current entry uses UTF8 for file name and comment.
     *
     * @return boolean
     */
    public boolean usesUTF8ForNames() {
        return languageEncodingFlag;
    }

    /**
     * whether the current entry will use UTF8 for file name and comment.
     *
     * @param b boolean
     */
    public void useUTF8ForNames(boolean b) {
        languageEncodingFlag = b;
    }

    /**
     * whether the current entry uses the data descriptor to store CRC
     * and size information
     *
     * @return boolean
     */
    public boolean usesDataDescriptor() {
        return dataDescriptorFlag;
    }

    /**
     * whether the current entry will use the data descriptor to store
     * CRC and size information
     *
     * @param b boolean
     */
    public void useDataDescriptor(boolean b) {
        dataDescriptorFlag = b;
    }

    /**
     * whether the current entry is encrypted
     *
     * @return boolean
     */
    public boolean usesEncryption() {
        return encryptionFlag;
    }

    /**
     * whether the current entry will be encrypted
     *
     * @param b boolean
     */
    public void useEncryption(boolean b) {
        encryptionFlag = b;
    }

    /**
     * whether the current entry is encrypted using strong encryption
     *
     * @return boolean
     */
    public boolean usesStrongEncryption() {
        return encryptionFlag && strongEncryptionFlag;
    }

    /**
     * whether the current entry will be encrypted  using strong encryption
     *
     * @param b boolean
     */
    public void useStrongEncryption(boolean b) {
        strongEncryptionFlag = b;
        if (b) {
            useEncryption(true);
        }
    }

    /**
     * Encodes the set bits in a form suitable for ZIP archives.
     *
     * @return byte[]
     */
    public byte[] encode() {
        byte[] result = new byte[2];
        encode(result, 0);
        return result;
    }

    /**
     * Encodes the set bits in a form suitable for ZIP archives.
     *
     * @param buf the output buffer
     * @param offset
     *         The offset within the output buffer of the first byte to be written.
     *         must be non-negative and no larger than <code>buf.length-2</code>
     */
    public void encode(byte[] buf, int offset) {
        _ApacheZipShort.putShort((dataDescriptorFlag ? DATA_DESCRIPTOR_FLAG : 0)
                          | (languageEncodingFlag ? UFT8_NAMES_FLAG : 0)
                          | (encryptionFlag ? ENCRYPTION_FLAG : 0)
                          | (strongEncryptionFlag ? STRONG_ENCRYPTION_FLAG : 0),
                buf, offset);
    }

    /**
     * Parses the supported flags from the given archive data.
     *
     * @param data local file header or a central directory entry.
     * @param offset offset at which the general purpose bit starts
     * @return _ApacheGeneralPurposeBit
     */
    public static _ApacheGeneralPurposeBit parse(final byte[] data, final int offset) {
        final int generalPurposeFlag = _ApacheZipShort.getValue(data, offset);
        _ApacheGeneralPurposeBit b = new _ApacheGeneralPurposeBit();
        b.useDataDescriptor((generalPurposeFlag & DATA_DESCRIPTOR_FLAG) != 0);
        b.useUTF8ForNames((generalPurposeFlag & UFT8_NAMES_FLAG) != 0);
        b.useStrongEncryption((generalPurposeFlag & STRONG_ENCRYPTION_FLAG)
                              != 0);
        b.useEncryption((generalPurposeFlag & ENCRYPTION_FLAG) != 0);
        return b;
    }

    @Override
    public int hashCode() {
        return 3 * (7 * (13 * (17 * (encryptionFlag ? 1 : 0)
                               + (strongEncryptionFlag ? 1 : 0))
                         + (languageEncodingFlag ? 1 : 0))
                    + (dataDescriptorFlag ? 1 : 0));
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof _ApacheGeneralPurposeBit) {
            _ApacheGeneralPurposeBit g = (_ApacheGeneralPurposeBit) o;
            return g.encryptionFlag == encryptionFlag
                    && g.strongEncryptionFlag == strongEncryptionFlag
                    && g.languageEncodingFlag == languageEncodingFlag
                    && g.dataDescriptorFlag == dataDescriptorFlag;
        }

        return false;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException ex) {
            // impossible
            throw new RuntimeException("_ApacheGeneralPurposeBit is not Cloneable?", ex); //NOSONAR
        }
    }
}
