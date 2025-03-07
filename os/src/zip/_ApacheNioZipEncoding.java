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
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/**
 * A _ApacheZipEncoding, which uses a java.nio {@link
 * java.nio.charset.Charset Charset} to encode names.
 *
 * <p>This implementation works for all cases under java-1.5 or
 * later. However, in java-1.4, some charsets don't have a java.nio
 * implementation, most notably the default ZIP encoding Cp437.</p>
 *
 * <p>The methods of this class are reentrant.</p>
 */
class _ApacheNioZipEncoding implements _ApacheZipEncoding {
    private final Charset charset;

    /**
     * Construct an NIO based zip encoding, which wraps the given
     * charset.
     *
     * @param charset The NIO charset to wrap.
     */
    public _ApacheNioZipEncoding(final Charset charset) {
        this.charset = charset;
    }

    /**
     * @see os._ApacheZipEncoding#canEncode(java.lang.String)
     */
    public boolean canEncode(final String name) {
        final CharsetEncoder enc = this.charset.newEncoder();
        enc.onMalformedInput(CodingErrorAction.REPORT);
        enc.onUnmappableCharacter(CodingErrorAction.REPORT);

        return enc.canEncode(name);
    }

    /**
     * @see os._ApacheZipEncoding#encode(java.lang.String)
     */
    public ByteBuffer encode(final String name) {
        final CharsetEncoder enc = this.charset.newEncoder();

        enc.onMalformedInput(CodingErrorAction.REPORT);
        enc.onUnmappableCharacter(CodingErrorAction.REPORT);

        final CharBuffer cb = CharBuffer.wrap(name);
        ByteBuffer out = ByteBuffer.allocate(name.length()
                                             + (name.length() + 1) / 2);

        while (cb.remaining() > 0) {
            final CoderResult res = enc.encode(cb, out, true);

            if (res.isUnmappable() || res.isMalformed()) {

                // write the unmappable characters in utf-16
                // pseudo-URL encoding style to ByteBuffer.
                if (res.length() * 6 > out.remaining()) {
                    out = _ApacheZipEncodingHelper.growBuffer(out, out.position()
                                                       + res.length() * 6);
                }

                for (int i = 0; i < res.length(); ++i) {
                    _ApacheZipEncodingHelper.appendSurrogate(out, cb.get());
                }

            } else if (res.isOverflow()) {

                out = _ApacheZipEncodingHelper.growBuffer(out, 0);

            } else if (res.isUnderflow()) {

                enc.flush(out);
                break;

            }
        }

        _ApacheZipEncodingHelper.prepareBufferForRead(out);

        return out;
    }

    /**
     * @see os._ApacheZipEncoding#decode(byte[])
     */
    public String decode(final byte[] data) throws IOException {
        return this.charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(data)).toString();
    }
}
