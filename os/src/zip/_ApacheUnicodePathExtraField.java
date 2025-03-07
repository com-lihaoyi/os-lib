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
 * Info-ZIP Unicode Path Extra Field (0x7075):
 *
 * <p>Stores the UTF-8 version of the file name field as stored in the
 * local header and central directory header.</p>
 *
 * <p>See <a href="https://www.pkware.com/documents/casestudies/APPNOTE.TXT">PKWARE's
 * APPNOTE.TXT, section 4.6.9</a>.</p>
 */
public class _ApacheUnicodePathExtraField extends _ApacheAbstractUnicodeExtraField {

    public static final _ApacheZipShort UPATH_ID = new _ApacheZipShort(0x7075);

    public _ApacheUnicodePathExtraField() {
    }

    /**
     * Assemble as unicode path extension from the name given as
     * text as well as the encoded bytes actually written to the archive.
     *
     * @param text The file name
     * @param bytes the bytes actually written to the archive
     * @param off The offset of the encoded filename in <code>bytes</code>.
     * @param len The length of the encoded filename or comment in
     * <code>bytes</code>.
     */
    public _ApacheUnicodePathExtraField(final String text, final byte[] bytes, final int off, final int len) {
        super(text, bytes, off, len);
    }

    /**
     * Assemble as unicode path extension from the name given as
     * text as well as the encoded bytes actually written to the archive.
     *
     * @param name The file name
     * @param bytes the bytes actually written to the archive
     */
    public _ApacheUnicodePathExtraField(final String name, final byte[] bytes) {
        super(name, bytes);
    }

    /** {@inheritDoc} */
    public _ApacheZipShort getHeaderId() {
        return UPATH_ID;
    }
}
