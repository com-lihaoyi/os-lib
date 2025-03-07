/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package os;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A fallback _ApacheZipEncoding, which uses a java.io means to encode names.
 *
 * <p>This implementation is not favorable for encodings other than
 * utf-8, because java.io encodes unmappable character as question
 * marks leading to unreadable ZIP entries on some operating
 * systems.</p>
 *
 * <p>Furthermore this implementation is unable to tell whether a
 * given name can be safely encoded or not.</p>
 *
 * <p>This implementation acts as a last resort implementation, when
 * neither {@link _ApacheSimple8BitZipEncoding} nor {@link _ApacheNioZipEncoding} is
 * available.</p>
 *
 * <p>The methods of this class are reentrant.</p>
 */
class _ApacheFallbackZipEncoding implements _ApacheZipEncoding {
    private final String charset;

    /**
     * Construct a fallback zip encoding, which uses the platform's
     * default charset.
     */
    public _ApacheFallbackZipEncoding() {
        this.charset = null;
    }

    /**
     * Construct a fallback zip encoding, which uses the given charset.
     *
     * @param charset The name of the charset or {@code null} for
     *                the platform's default character set.
     */
    public _ApacheFallbackZipEncoding(final String charset) {
        this.charset = charset;
    }

    /**
     * @see os._ApacheZipEncoding#canEncode(java.lang.String)
     */
    public boolean canEncode(final String name) {
        return true;
    }

    /**
     * @see os._ApacheZipEncoding#encode(java.lang.String)
     */
    public ByteBuffer encode(final String name) throws IOException {
        if (this.charset == null) { // i.e. use default charset, see no-args constructor
            return ByteBuffer.wrap(name.getBytes());
        } else {
            return ByteBuffer.wrap(name.getBytes(this.charset));
        }
    }

    /**
     * @see os._ApacheZipEncoding#decode(byte[])
     */
    public String decode(final byte[] data) throws IOException {
        if (this.charset == null) { // i.e. use default charset, see no-args constructor
            return new String(data);
        } else {
            return new String(data, this.charset);
        }
    }
}
