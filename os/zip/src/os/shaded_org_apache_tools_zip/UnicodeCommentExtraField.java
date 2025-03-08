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

/**
 * Info-ZIP Unicode Comment Extra Field (0x6375):
 *
 * <p>Stores the UTF-8 version of the file comment as stored in the
 * central directory header.</p>
 *
 * <p>See <a href="https://www.pkware.com/documents/casestudies/APPNOTE.TXT">PKWARE's
 * APPNOTE.TXT, section 4.6.8</a>.</p>
 *
 */
class UnicodeCommentExtraField extends AbstractUnicodeExtraField {

    public static final ZipShort UCOM_ID = new ZipShort(0x6375);

    public UnicodeCommentExtraField() {
    }

    /**
     * Assemble as unicode comment extension from the name given as
     * text as well as the encoded bytes actually written to the archive.
     *
     * @param text The file name
     * @param bytes the bytes actually written to the archive
     * @param off The offset of the encoded comment in <code>bytes</code>.
     * @param len The length of the encoded comment or comment in
     * <code>bytes</code>.
     */
    public UnicodeCommentExtraField(final String text, final byte[] bytes, final int off,
                                    final int len) {
        super(text, bytes, off, len);
    }

    /**
     * Assemble as unicode comment extension from the comment given as
     * text as well as the bytes actually written to the archive.
     *
     * @param comment The file comment
     * @param bytes the bytes actually written to the archive
     */
    public UnicodeCommentExtraField(final String comment, final byte[] bytes) {
        super(comment, bytes);
    }

    /** {@inheritDoc} */
    public ZipShort getHeaderId() {
        return UCOM_ID;
    }

}
