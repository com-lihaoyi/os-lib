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

import static os._ApacheZipConstants.DATA_DESCRIPTOR_MIN_VERSION;
import static os._ApacheZipConstants.DWORD;
import static os._ApacheZipConstants.INITIAL_VERSION;
import static os._ApacheZipConstants.SHORT;
import static os._ApacheZipConstants.WORD;
import static os._ApacheZipConstants.ZIP64_MAGIC;
import static os._ApacheZipConstants.ZIP64_MAGIC_SHORT;
import static os._ApacheZipConstants.ZIP64_MIN_VERSION;
import static os._ApacheZipLong.putLong;
import static os._ApacheZipShort.putShort;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipException;

/**
 * Reimplementation of {@link java.util.zip.ZipOutputStream
 * java.util.zip.ZipOutputStream} that does handle the extended
 * functionality of this package, especially internal/external file
 * attributes and extra fields with different layouts for local file
 * data and central directory entries.
 *
 * <p>This class will try to use {@link java.io.RandomAccessFile
 * RandomAccessFile} when you know that the output is going to go to a
 * file.</p>
 *
 * <p>If RandomAccessFile cannot be used, this implementation will use
 * a Data Descriptor to store size and CRC information for {@link
 * #DEFLATED DEFLATED} entries, this means, you don't need to
 * calculate them yourself.  Unfortunately this is not possible for
 * the {@link #STORED STORED} method, here setting the CRC and
 * uncompressed size information is required before {@link
 * #putNextEntry putNextEntry} can be called.</p>
 *
 * <p>As of Apache Ant 1.9.0 it transparently supports Zip64
 * extensions and thus individual entries and archives larger than 4
 * GB or with more than 65536 entries in most cases but explicit
 * control is provided via {@link #setUseZip64}.  If the stream can not
 * user RandomAccessFile and you try to write a _ApacheZipEntry of
 * unknown size then Zip64 extensions will be disabled by default.</p>
 */
public class _ApacheZipOutputStream extends FilterOutputStream {

    private static final int BUFFER_SIZE = 512;
    private static final int LFH_SIG_OFFSET = 0;
    private static final int LFH_VERSION_NEEDED_OFFSET = 4;
    private static final int LFH_GPB_OFFSET = 6;
    private static final int LFH_METHOD_OFFSET = 8;
    private static final int LFH_TIME_OFFSET = 10;
    private static final int LFH_CRC_OFFSET = 14;
    private static final int LFH_COMPRESSED_SIZE_OFFSET = 18;
    private static final int LFH_ORIGINAL_SIZE_OFFSET = 22;
    private static final int LFH_FILENAME_LENGTH_OFFSET = 26;
    private static final int LFH_EXTRA_LENGTH_OFFSET = 28;
    private static final int LFH_FILENAME_OFFSET = 30;
    private static final int CFH_SIG_OFFSET = 0;
    private static final int CFH_VERSION_MADE_BY_OFFSET = 4;
    private static final int CFH_VERSION_NEEDED_OFFSET = 6;
    private static final int CFH_GPB_OFFSET = 8;
    private static final int CFH_METHOD_OFFSET = 10;
    private static final int CFH_TIME_OFFSET = 12;
    private static final int CFH_CRC_OFFSET = 16;
    private static final int CFH_COMPRESSED_SIZE_OFFSET = 20;
    private static final int CFH_ORIGINAL_SIZE_OFFSET = 24;
    private static final int CFH_FILENAME_LENGTH_OFFSET = 28;
    private static final int CFH_EXTRA_LENGTH_OFFSET = 30;
    private static final int CFH_COMMENT_LENGTH_OFFSET = 32;
    private static final int CFH_DISK_NUMBER_OFFSET = 34;
    private static final int CFH_INTERNAL_ATTRIBUTES_OFFSET = 36;
    private static final int CFH_EXTERNAL_ATTRIBUTES_OFFSET = 38;
    private static final int CFH_LFH_OFFSET = 42;
    private static final int CFH_FILENAME_OFFSET = 46;

    /**
     * indicates if this archive is finished.
     */
    private boolean finished = false;

    /*
     * Apparently Deflater.setInput gets slowed down a lot on Sun JVMs
     * when it gets handed a really big buffer.  See
     * https://issues.apache.org/bugzilla/show_bug.cgi?id=45396
     *
     * Using a buffer size of 8 kB proved to be a good compromise
     */
    private static final int DEFLATER_BLOCK_SIZE = 8192;

    /**
     * Compression method for deflated entries.
     *
     * @since 1.1
     */
    public static final int DEFLATED = java.util.zip.ZipEntry.DEFLATED;

    /**
     * Default compression level for deflated entries.
     *
     * @since Ant 1.7
     */
    public static final int DEFAULT_COMPRESSION = Deflater.DEFAULT_COMPRESSION;

    /**
     * Compression method for stored entries.
     *
     * @since 1.1
     */
    public static final int STORED = java.util.zip.ZipEntry.STORED;

    /**
     * default encoding for file names and comment.
     */
    static final String DEFAULT_ENCODING = null;

    /**
     * General purpose flag, which indicates that filenames are
     * written in utf-8.
     * @deprecated use {@link _ApacheGeneralPurposeBit#UFT8_NAMES_FLAG} instead
     */
    @Deprecated
    public static final int EFS_FLAG = _ApacheGeneralPurposeBit.UFT8_NAMES_FLAG;

    private static final byte[] EMPTY = new byte[0];

    /**
     * Current entry.
     *
     * @since 1.1
     */
    private CurrentEntry entry;

    /**
     * The file comment.
     *
     * @since 1.1
     */
    private String comment = "";

    /**
     * Compression level for next entry.
     *
     * @since 1.1
     */
    private int level = DEFAULT_COMPRESSION;

    /**
     * Has the compression level changed when compared to the last
     * entry?
     *
     * @since 1.5
     */
    private boolean hasCompressionLevelChanged = false;

    /**
     * Default compression method for next entry.
     *
     * @since 1.1
     */
    private int method = java.util.zip.ZipEntry.DEFLATED;

    /**
     * List of ZipEntries written so far.
     *
     * @since 1.1
     */
    private final List<_ApacheZipEntry> entries = new LinkedList<>();

    /**
     * CRC instance to avoid parsing DEFLATED data twice.
     *
     * @since 1.1
     */
    private final CRC32 crc = new CRC32();

    /**
     * Count the bytes written to out.
     *
     * @since 1.1
     */
    private long written = 0;

    /**
     * Start of central directory.
     *
     * @since 1.1
     */
    private long cdOffset = 0;

    /**
     * Length of central directory.
     *
     * @since 1.1
     */
    private long cdLength = 0;

    /**
     * Helper, a 0 as _ApacheZipShort.
     *
     * @since 1.1
     */
    private static final byte[] ZERO = {0, 0};

    /**
     * Helper, a 0 as _ApacheZipLong.
     *
     * @since 1.1
     */
    private static final byte[] LZERO = {0, 0, 0, 0};

    private static final byte[] ONE = _ApacheZipLong.getBytes(1L);

    /**
     * Holds the offsets of the LFH starts for each entry.
     *
     * @since 1.1
     */
    private final Map<_ApacheZipEntry, Long> offsets = new HashMap<>();

    /**
     * The encoding to use for filenames and the file comment.
     *
     * <p>For a list of possible values see <a
     * href="https://docs.oracle.com/javase/8/docs/technotes/guides/intl/encoding.doc.html">
     * https://docs.oracle.com/javase/8/docs/technotes/guides/intl/encoding.doc.html</a>.
     * Defaults to the platform's default character encoding.</p>
     *
     * @since 1.3
     */
    private String encoding = null;

    /**
     * The zip encoding to use for filenames and the file comment.
     * <p>
     * This field is of internal use and will be set in {@link
     * #setEncoding(String)}.
     * </p>
     */
    private _ApacheZipEncoding zipEncoding =
        _ApacheZipEncodingHelper.getZipEncoding(DEFAULT_ENCODING);

   // CheckStyle:VisibilityModifier OFF - bc

    /**
     * This Deflater object is used for output.
     *
     */
    protected final Deflater def = new Deflater(level, true);

    /**
     * This buffer serves as a Deflater.
     *
     * <p>This attribute is only protected to provide a level of API
     * backwards compatibility.  This class used to extend {@link
     * java.util.zip.DeflaterOutputStream DeflaterOutputStream} up to
     * Revision 1.13.</p>
     *
     * @since 1.14
     */
    protected byte[] buf = new byte[BUFFER_SIZE];

    // CheckStyle:VisibilityModifier ON

    /**
     * Optional random access output.
     *
     * @since 1.14
     */
    private final RandomAccessFile raf;

    /**
     * whether to use the general purpose bit flag when writing UTF-8
     * filenames or not.
     */
    private boolean useUTF8Flag = true;

    /**
     * Whether to encode non-encodable file names as UTF-8.
     */
    private boolean fallbackToUTF8 = false;

    /**
     * whether to create _ApacheUnicodePathExtraField-s for each entry.
     */
    private UnicodeExtraFieldPolicy createUnicodeExtraFields = UnicodeExtraFieldPolicy.NEVER;

    /**
     * Whether anything inside this archive has used a ZIP64 feature.
     */
    private boolean hasUsedZip64 = false;

    private _ApacheZip64Mode zip64Mode = _ApacheZip64Mode.AsNeeded;

    private final Calendar calendarInstance = Calendar.getInstance();

    /**
     * Temporary buffer used for the {@link #write(int)} method.
     */
    private final byte[] oneByte = new byte[1];

    /**
     * Creates a new ZIP OutputStream filtering the underlying stream.
     * @param out the outputstream to zip
     * @since 1.1
     */
    public _ApacheZipOutputStream(OutputStream out) {
        super(out);
        this.raf = null;
    }

    /**
     * Creates a new ZIP OutputStream writing to a File.  Will use
     * random access if possible.
     * @param file the file to zip to
     * @since 1.14
     * @throws IOException on error
     */
    public _ApacheZipOutputStream(File file) throws IOException {
        super(null);
        RandomAccessFile ranf = null;
        try {
            ranf = new RandomAccessFile(file, "rw");
            ranf.setLength(0);
        } catch (IOException e) {
            if (ranf != null) {
                try {
                    ranf.close();
                } catch (IOException inner) { // NOPMD
                    // ignore
                }
                ranf = null;
            }
            out = Files.newOutputStream(file.toPath());
        }
        raf = ranf;
    }

    /**
     * This method indicates whether this archive is writing to a
     * seekable stream (i.e., to a random access file).
     *
     * <p>For seekable streams, you don't need to calculate the CRC or
     * uncompressed size for {@link #STORED} entries before
     * invoking {@link #putNextEntry}.
     * @return true if seekable
     * @since 1.17
     */
    public boolean isSeekable() {
        return raf != null;
    }

    /**
     * The encoding to use for filenames and the file comment.
     *
     * <p>For a list of possible values see <a
     * href="https://docs.oracle.com/javase/8/docs/technotes/guides/intl/encoding.doc.html">
     * https://docs.oracle.com/javase/8/docs/technotes/guides/intl/encoding.doc.html</a>.
     * Defaults to the platform's default character encoding.</p>
     * @param encoding the encoding value
     * @since 1.3
     */
    public void setEncoding(final String encoding) {
        this.encoding = encoding;
        this.zipEncoding = _ApacheZipEncodingHelper.getZipEncoding(encoding);
        if (useUTF8Flag && !_ApacheZipEncodingHelper.isUTF8(encoding)) {
            useUTF8Flag = false;
        }
    }

    /**
     * The encoding to use for filenames and the file comment.
     *
     * @return null if using the platform's default character encoding.
     *
     * @since 1.3
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Whether to set the language encoding flag if the file name
     * encoding is UTF-8.
     *
     * <p>Defaults to true.</p>
     *
     * @param b boolean
     */
    public void setUseLanguageEncodingFlag(boolean b) {
        useUTF8Flag = b && _ApacheZipEncodingHelper.isUTF8(encoding);
    }

    /**
     * Whether to create Unicode Extra Fields.
     *
     * <p>Defaults to NEVER.</p>
     *
     * @param b boolean
     */
    public void setCreateUnicodeExtraFields(UnicodeExtraFieldPolicy b) {
        createUnicodeExtraFields = b;
    }

    /**
     * Whether to fall back to UTF and the language encoding flag if
     * the file name cannot be encoded using the specified encoding.
     *
     * <p>Defaults to false.</p>
     *
     * @param b boolean
     */
    public void setFallbackToUTF8(boolean b) {
        fallbackToUTF8 = b;
    }

    /**
     * Whether Zip64 extensions will be used.
     *
     * <p>When setting the mode to {@link _ApacheZip64Mode#Never Never},
     * {@link #putNextEntry}, {@link #closeEntry}, {@link
     * #finish} or {@link #close} may throw a {@link
     * _ApacheZip64RequiredException} if the entry's size or the total size
     * of the archive exceeds 4GB or there are more than 65536 entries
     * inside the archive.  Any archive created in this mode will be
     * readable by implementations that don't support Zip64.</p>
     *
     * <p>When setting the mode to {@link _ApacheZip64Mode#Always Always},
     * Zip64 extensions will be used for all entries.  Any archive
     * created in this mode may be unreadable by implementations that
     * don't support Zip64 even if all its contents would be.</p>
     *
     * <p>When setting the mode to {@link _ApacheZip64Mode#AsNeeded
     * AsNeeded}, Zip64 extensions will transparently be used for
     * those entries that require them.  This mode can only be used if
     * the uncompressed size of the {@link _ApacheZipEntry} is known
     * when calling {@link #putNextEntry} or the archive is written
     * to a seekable output (i.e. you have used the {@link
     * #_ApacheZipOutputStream(java.io.File) File-arg constructor}) -
     * this mode is not valid when the output stream is not seekable
     * and the uncompressed size is unknown when {@link
     * #putNextEntry} is called.</p>
     *
     * <p>If no entry inside the resulting archive requires Zip64
     * extensions then {@link _ApacheZip64Mode#Never Never} will create the
     * smallest archive.  {@link _ApacheZip64Mode#AsNeeded AsNeeded} will
     * create a slightly bigger archive if the uncompressed size of
     * any entry has initially been unknown and create an archive
     * identical to {@link _ApacheZip64Mode#Never Never} otherwise.  {@link
     * _ApacheZip64Mode#Always Always} will create an archive that is at
     * least 24 bytes per entry bigger than the one {@link
     * _ApacheZip64Mode#Never Never} would create.</p>
     *
     * <p>Defaults to {@link _ApacheZip64Mode#AsNeeded AsNeeded} unless
     * {@link #putNextEntry} is called with an entry of unknown
     * size and data is written to a non-seekable stream - in this
     * case the default is {@link _ApacheZip64Mode#Never Never}.</p>
     *
     * @param mode _ApacheZip64Mode
     * @since 1.3
     */
    public void setUseZip64(_ApacheZip64Mode mode) {
        zip64Mode = mode;
    }

    /**
     * Finish writing the archive.
     *
     * @throws _ApacheZip64RequiredException if the archive's size exceeds 4
     * GByte or there are more than 65535 entries inside the archive
     * and {@link #setUseZip64} is {@link _ApacheZip64Mode#Never}.
     */
    public void finish() throws IOException {
        if (finished) {
            throw new IOException("This archive has already been finished");
        }

        if (entry != null) {
            closeEntry();
        }

        cdOffset = written;
        writeCentralDirectoryInChunks();
        cdLength = written - cdOffset;
        writeZip64CentralDirectory();
        writeCentralDirectoryEnd();
        offsets.clear();
        entries.clear();
        def.end();
        finished = true;
    }

    private void writeCentralDirectoryInChunks() throws IOException {
        final int NUM_PER_WRITE = 1000;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(70 * NUM_PER_WRITE);
        int count = 0;
        for (_ApacheZipEntry ze : entries) {
            byteArrayOutputStream.write(createCentralFileHeader(ze));
            if (++count > NUM_PER_WRITE) {
                writeCounted(byteArrayOutputStream.toByteArray());
                byteArrayOutputStream.reset();
                count = 0;
            }
        }
        writeCounted(byteArrayOutputStream.toByteArray());
    }

    /**
     * Writes all necessary data for this entry.
     *
     * @since 1.1
     * @throws IOException on error
     * @throws _ApacheZip64RequiredException if the entry's uncompressed or
     * compressed size exceeds 4 GByte and {@link #setUseZip64}
     * is {@link _ApacheZip64Mode#Never}.
     */
    public void closeEntry() throws IOException {
        preClose();

        flushDeflater();

        final _ApacheZip64Mode effectiveMode = getEffectiveZip64Mode(entry.entry);
        long bytesWritten = written - entry.dataStart;
        long realCrc = crc.getValue();
        crc.reset();

        final boolean actuallyNeedsZip64 =
            handleSizesAndCrc(bytesWritten, realCrc, effectiveMode);

        closeEntry(actuallyNeedsZip64);
    }

    private void closeEntry(boolean actuallyNeedsZip64) throws IOException {
        if (raf != null) {
            rewriteSizesAndCrc(actuallyNeedsZip64);
        }

        writeDataDescriptor(entry.entry);
        entry = null;
    }

    private void preClose() throws IOException {
        if (finished) {
            throw new IOException("Stream has already been finished");
        }

        if (entry == null) {
            throw new IOException("No current entry to close");
        }

        if (!entry.hasWritten) {
            write(EMPTY, 0, 0);
        }
    }

    /**
     * Ensures all bytes sent to the deflater are written to the stream.
     */
    private void flushDeflater() throws IOException {
        if (entry.entry.getMethod() == DEFLATED) {
            def.finish();
            while (!def.finished()) {
                deflate();
            }
        }
    }

    /**
     * Ensures the current entry's size and CRC information is set to
     * the values just written, verifies it isn't too big in the
     * _ApacheZip64Mode.Never case and returns whether the entry would
     * require a Zip64 extra field.
     *
     * @param bytesWritten long
     * @param crc long
     * @param effectiveMode _ApacheZip64Mode
     * @return boolean
     * @throws ZipException if size or CRC is incorrect
     */
    private boolean handleSizesAndCrc(long bytesWritten, long crc,
                                      _ApacheZip64Mode effectiveMode)
        throws ZipException {
        if (entry.entry.getMethod() == DEFLATED) {
            /* It turns out def.getBytesRead() returns wrong values if
             * the size exceeds 4 GB on Java < Java7
            entry.entry.setSize(def.getBytesRead());
            */
            entry.entry.setSize(entry.bytesRead);
            entry.entry.setCompressedSize(bytesWritten);
            entry.entry.setCrc(crc);

            def.reset();
        } else if (raf == null) {
            if (entry.entry.getCrc() != crc) {
                throw new ZipException("bad CRC checksum for entry "
                                       + entry.entry.getName() + ": "
                                       + Long.toHexString(entry.entry.getCrc())
                                       + " instead of "
                                       + Long.toHexString(crc));
            }

            if (entry.entry.getSize() != bytesWritten) {
                throw new ZipException("bad size for entry "
                                       + entry.entry.getName() + ": "
                                       + entry.entry.getSize()
                                       + " instead of "
                                       + bytesWritten);
            }
        } else { /* method is STORED and we used RandomAccessFile */
            entry.entry.setSize(bytesWritten);
            entry.entry.setCompressedSize(bytesWritten);
            entry.entry.setCrc(crc);
        }

        return checkIfNeedsZip64(effectiveMode);
    }

    /**
     * Ensures the current entry's size and CRC information is set to
     * the values just written, verifies it isn't too big in the
     * _ApacheZip64Mode.Never case and returns whether the entry would
     * require a Zip64 extra field.
     *
     * @param effectiveMode _ApacheZip64Mode
     * @return boolean
     * @throws ZipException if the entry is too big for _ApacheZip64Mode.Never
     */
    private boolean checkIfNeedsZip64(_ApacheZip64Mode effectiveMode)
            throws ZipException {
        final boolean actuallyNeedsZip64 = isZip64Required(entry.entry,
                                                           effectiveMode);
        if (actuallyNeedsZip64 && effectiveMode == _ApacheZip64Mode.Never) {
            throw new _ApacheZip64RequiredException(_ApacheZip64RequiredException
                                             .getEntryTooBigMessage(entry.entry));
        }
        return actuallyNeedsZip64;
    }

    private boolean isZip64Required(_ApacheZipEntry entry1, _ApacheZip64Mode requestedMode) {
        return requestedMode == _ApacheZip64Mode.Always || isTooLageForZip32(entry1);
    }

    private boolean isTooLageForZip32(_ApacheZipEntry zipArchiveEntry) {
        return zipArchiveEntry.getSize() >= ZIP64_MAGIC
            || zipArchiveEntry.getCompressedSize() >= ZIP64_MAGIC;
    }

    /**
     * When using random access output, write the local file header
     * and potentially the ZIP64 extra containing the correct CRC and
     * compressed/uncompressed sizes.
     *
     * @param actuallyNeedsZip64 boolean
     */
    private void rewriteSizesAndCrc(boolean actuallyNeedsZip64)
        throws IOException {
        long save = raf.getFilePointer();

        raf.seek(entry.localDataStart);
        writeOut(_ApacheZipLong.getBytes(entry.entry.getCrc()));
        if (!hasZip64Extra(entry.entry) || !actuallyNeedsZip64) {
            writeOut(_ApacheZipLong.getBytes(entry.entry.getCompressedSize()));
            writeOut(_ApacheZipLong.getBytes(entry.entry.getSize()));
        } else {
            writeOut(_ApacheZipLong.ZIP64_MAGIC.getBytes());
            writeOut(_ApacheZipLong.ZIP64_MAGIC.getBytes());
        }

        if (hasZip64Extra(entry.entry)) {
            // seek to ZIP64 extra, skip header and size information
            raf.seek(entry.localDataStart + 3 * WORD + 2 * SHORT
                     + getName(entry.entry).limit() + 2 * SHORT);
            // inside the ZIP64 extra uncompressed size comes
            // first, unlike the LFH, CD or data descriptor
            writeOut(_ApacheZipEightByteInteger.getBytes(entry.entry.getSize()));
            writeOut(_ApacheZipEightByteInteger.getBytes(entry.entry.getCompressedSize()));

            if (!actuallyNeedsZip64) {
                // do some cleanup:
                // * rewrite version needed to extract
                raf.seek(entry.localDataStart  - 5 * SHORT);
                writeOut(_ApacheZipShort.getBytes(INITIAL_VERSION));

                // * remove ZIP64 extra so it doesn't get written
                //   to the central directory
                entry.entry.removeExtraField(_ApacheZip64ExtendedInformationExtraField
                                             .HEADER_ID);
                entry.entry.setExtra();

                // * reset hasUsedZip64 if it has been set because
                //   of this entry
                if (entry.causedUseOfZip64) {
                    hasUsedZip64 = false;
                }
            }
        }
        raf.seek(save);
    }

    /**
     * Put the specified entry into the archive.
     *
     * @throws _ApacheZip64RequiredException if the entry's uncompressed or
     * compressed size is known to exceed 4 GByte and {@link #setUseZip64}
     * is {@link _ApacheZip64Mode#Never}.
     */
    public void putNextEntry(_ApacheZipEntry archiveEntry) throws IOException {
        if (finished) {
            throw new IOException("Stream has already been finished");
        }

        if (entry != null) {
            closeEntry();
        }

        entry = new CurrentEntry(archiveEntry);
        entries.add(entry.entry);

        setDefaults(entry.entry);

        final _ApacheZip64Mode effectiveMode = getEffectiveZip64Mode(entry.entry);
        validateSizeInformation(effectiveMode);

        if (shouldAddZip64Extra(entry.entry, effectiveMode)) {

            _ApacheZip64ExtendedInformationExtraField z64 = getZip64Extra(entry.entry);

            // just a placeholder, real data will be in data
            // descriptor or inserted later via RandomAccessFile
            _ApacheZipEightByteInteger size = _ApacheZipEightByteInteger.ZERO;
            _ApacheZipEightByteInteger compressedSize = _ApacheZipEightByteInteger.ZERO;
            if (entry.entry.getMethod() == STORED
                && entry.entry.getSize() != -1) {
                // actually, we already know the sizes
                size = new _ApacheZipEightByteInteger(entry.entry.getSize());
                compressedSize = size;
            }
            z64.setSize(size);
            z64.setCompressedSize(compressedSize);
            entry.entry.setExtra();
        }

        if (entry.entry.getMethod() == DEFLATED && hasCompressionLevelChanged) {
            def.setLevel(level);
            hasCompressionLevelChanged = false;
        }
        writeLocalFileHeader(entry.entry);
    }

    /**
     * Provides default values for compression method and last
     * modification time.
     *
     * @param entry _ApacheZipEntry
     */
    private void setDefaults(_ApacheZipEntry entry) {
        if (entry.getMethod() == -1) { // not specified
            entry.setMethod(method);
        }

        if (entry.getTime() == -1) { // not specified
            entry.setTime(System.currentTimeMillis());
        }
    }

    /**
     * Throws an exception if the size is unknown for a stored entry
     * that is written to a non-seekable output or the entry is too
     * big to be written without Zip64 extra but the mode has been set
     * to Never.
     *
     * @param effectiveMode _ApacheZip64Mode
     */
    private void validateSizeInformation(_ApacheZip64Mode effectiveMode)
        throws ZipException {
        // Size/CRC not required if RandomAccessFile is used
        if (entry.entry.getMethod() == STORED && raf == null) {
            if (entry.entry.getSize() == -1) {
                throw new ZipException("uncompressed size is required for"
                                       + " STORED method when not writing to a"
                                       + " file");
            }
            if (entry.entry.getCrc() == -1) {
                throw new ZipException("crc checksum is required for STORED"
                                       + " method when not writing to a file");
            }
            entry.entry.setCompressedSize(entry.entry.getSize());
        }

        if ((entry.entry.getSize() >= ZIP64_MAGIC
             || entry.entry.getCompressedSize() >= ZIP64_MAGIC)
            && effectiveMode == _ApacheZip64Mode.Never) {
            throw new _ApacheZip64RequiredException(_ApacheZip64RequiredException
                                             .getEntryTooBigMessage(entry.entry));
        }
    }

    /**
     * Whether to add a Zip64 extended information extra field to the
     * local file header.
     *
     * <p>Returns true if</p>
     *
     * <ul>
     * <li>mode is Always</li>
     * <li>or we already know it is going to be needed</li>
     * <li>or the size is unknown and we can ensure it won't hurt
     * other implementations if we add it (i.e. we can erase its
     * usage</li>
     * </ul>
     *
     * @param entry _ApacheZipEntry
     * @param mode _ApacheZip64Mode
     */
    private boolean shouldAddZip64Extra(_ApacheZipEntry entry, _ApacheZip64Mode mode) {
        return mode == _ApacheZip64Mode.Always
            || entry.getSize() >= ZIP64_MAGIC
            || entry.getCompressedSize() >= ZIP64_MAGIC
            || (entry.getSize() == -1
                && raf != null && mode != _ApacheZip64Mode.Never);
    }

    /**
     * Set the file comment.
     *
     * @param comment the comment
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * Sets the compression level for subsequent entries.
     *
     * <p>Default is Deflater.DEFAULT_COMPRESSION.</p>
     *
     * @param level the compression level.
     * @throws IllegalArgumentException if an invalid compression
     * level is specified.
     * @since 1.1
     */
    public void setLevel(int level) {
        if (level < Deflater.DEFAULT_COMPRESSION
            || level > Deflater.BEST_COMPRESSION) {
            throw new IllegalArgumentException("Invalid compression level: "
                                               + level);
        }
        if (this.level == level) {
            return;
        }
        hasCompressionLevelChanged = true;
        this.level = level;
    }

    /**
     * Sets the default compression method for subsequent entries.
     *
     * <p>Default is DEFLATED.</p>
     *
     * @param method an <code>int</code> from java.util.zip.ZipEntry
     * @since 1.1
     */
    public void setMethod(int method) {
        this.method = method;
    }

    /**
     * Whether this stream is able to write the given entry.
     *
     * <p>May return false if it is set up to use encryption or a
     * compression method that hasn't been implemented yet.</p>
     *
     * @param ae _ApacheZipEntry
     * @return boolean
     */
    public boolean canWriteEntryData(_ApacheZipEntry ae) {
        return _ApacheZipUtil.canHandleEntryData(ae);
    }

    /**
     * Writes a byte to ZIP entry.
     *
     * @param b the byte to write
     * @throws IOException on error
     * @since Ant 1.10.10
     */
    @Override
    public void write(int b) throws IOException {
        oneByte[0] = (byte) (b & 0xff);
        write(oneByte, 0, 1);
    }

    /**
     * Writes bytes to ZIP entry.
     *
     * @param b the byte array to write
     * @param offset the start position to write from
     * @param length the number of bytes to write
     * @throws IOException on error
     */
    @Override
    public void write(byte[] b, int offset, int length) throws IOException {
        if (entry == null) {
            throw new IllegalStateException("No current entry");
        }
        _ApacheZipUtil.checkRequestedFeatures(entry.entry);
        entry.hasWritten = true;
        if (entry.entry.getMethod() == DEFLATED) {
            writeDeflated(b, offset, length);
        } else {
            writeCounted(b, offset, length);
        }
        crc.update(b, offset, length);
    }

    /**
     * Write bytes to output or random access file.
     *
     * @param data the byte array to write
     * @throws IOException on error
     */
    private void writeCounted(byte[] data) throws IOException {
        writeCounted(data, 0, data.length);
    }

    private void writeCounted(byte[] data, int offset, int length) throws IOException {
        writeOut(data, offset, length);
        written += length;
    }

    /**
     * write implementation for DEFLATED entries.
     *
     * @param b byte[]
     * @param offset int
     * @param length int
     */
    private void writeDeflated(byte[] b, int offset, int length)
        throws IOException {
        if (length > 0 && !def.finished()) {
            entry.bytesRead += length;
            if (length <= DEFLATER_BLOCK_SIZE) {
                def.setInput(b, offset, length);
                deflateUntilInputIsNeeded();
            } else {
                final int fullblocks = length / DEFLATER_BLOCK_SIZE;
                for (int i = 0; i < fullblocks; i++) {
                    def.setInput(b, offset + i * DEFLATER_BLOCK_SIZE,
                                 DEFLATER_BLOCK_SIZE);
                    deflateUntilInputIsNeeded();
                }
                final int done = fullblocks * DEFLATER_BLOCK_SIZE;
                if (done < length) {
                    def.setInput(b, offset + done, length - done);
                    deflateUntilInputIsNeeded();
                }
            }
        }
    }

    /**
     * Closes this output stream and releases any system resources
     * associated with the stream.
     *
     * @throws IOException  if an I/O error occurs.
     * @throws _ApacheZip64RequiredException if the archive's size exceeds 4
     * GByte or there are more than 65535 entries inside the archive
     * and {@link #setUseZip64} is {@link _ApacheZip64Mode#Never}.
     */
    @Override
    public void close() throws IOException {
        if (!finished) {
            finish();
        }
        destroy();
    }

    /**
     * Flushes this output stream and forces any buffered output bytes
     * to be written out to the stream.
     *
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public void flush() throws IOException {
        if (out != null) {
            out.flush();
        }
    }

    /*
     * Various ZIP constants
     */
    /**
     * local file header signature
     *
     * @since 1.1
     */
    protected static final byte[] LFH_SIG = _ApacheZipLong.LFH_SIG.getBytes(); //NOSONAR
    /**
     * data descriptor signature
     *
     * @since 1.1
     */
    protected static final byte[] DD_SIG = _ApacheZipLong.DD_SIG.getBytes(); //NOSONAR
    /**
     * central file header signature
     *
     * @since 1.1
     */
    protected static final byte[] CFH_SIG = _ApacheZipLong.CFH_SIG.getBytes(); //NOSONAR
    /**
     * end of central dir signature
     *
     * @since 1.1
     */
    protected static final byte[] EOCD_SIG = _ApacheZipLong.getBytes(0X06054B50L); //NOSONAR
    /**
     * ZIP64 end of central dir signature
     */
    static final byte[] ZIP64_EOCD_SIG = _ApacheZipLong.getBytes(0X06064B50L); //NOSONAR
    /**
     * ZIP64 end of central dir locator signature
     */
    static final byte[] ZIP64_EOCD_LOC_SIG = _ApacheZipLong.getBytes(0X07064B50L); //NOSONAR

    /**
     * Writes next block of compressed data to the output stream.
     *
     * @throws IOException on error
     * @since 1.14
     */
    protected final void deflate() throws IOException {
        int len = def.deflate(buf, 0, buf.length);
        if (len > 0) {
            writeCounted(buf, 0, len);
        }
    }

    /**
     * Writes the local file header entry
     *
     * @param ze the entry to write
     * @throws IOException on error
     * @since 1.1
     */
    protected void writeLocalFileHeader(_ApacheZipEntry ze) throws IOException {

        boolean encodable = zipEncoding.canEncode(ze.getName());
        ByteBuffer name = getName(ze);

        if (createUnicodeExtraFields != UnicodeExtraFieldPolicy.NEVER) {
            addUnicodeExtraFields(ze, encodable, name);
        }

        final byte[] localHeader = createLocalFileHeader(ze, name, encodable);
        final long localHeaderStart = written;
        offsets.put(ze, localHeaderStart);
        entry.localDataStart = localHeaderStart + LFH_CRC_OFFSET; // At crc offset
        writeCounted(localHeader);
        entry.dataStart = written;
    }

    private byte[] createLocalFileHeader(_ApacheZipEntry ze, ByteBuffer name, boolean encodable)  {
        byte[] extra = ze.getLocalFileDataExtra();
        final int nameLen = name.limit() - name.position();
        int len = LFH_FILENAME_OFFSET + nameLen + extra.length;
        byte[] buf = new byte[len];

        System.arraycopy(LFH_SIG,  0, buf, LFH_SIG_OFFSET, WORD);

        //store method in local variable to prevent multiple method calls
        final int zipMethod = ze.getMethod();

        putShort(versionNeededToExtract(zipMethod, hasZip64Extra(ze)),
                 buf, LFH_VERSION_NEEDED_OFFSET);

        _ApacheGeneralPurposeBit generalPurposeBit =
            getGeneralPurposeBits(zipMethod, !encodable && fallbackToUTF8);
        generalPurposeBit.encode(buf, LFH_GPB_OFFSET);

        // compression method
        putShort(zipMethod, buf, LFH_METHOD_OFFSET);

        _ApacheZipUtil.toDosTime(calendarInstance, ze.getTime(), buf, LFH_TIME_OFFSET);

        // CRC
        if (zipMethod == DEFLATED || raf != null) {
            System.arraycopy(LZERO, 0, buf, LFH_CRC_OFFSET, WORD);
        } else {
            putLong(ze.getCrc(), buf, LFH_CRC_OFFSET);
        }

        // compressed length
        // uncompressed length
        if (hasZip64Extra(entry.entry)) {
            // point to ZIP64 extended information extra field for
            // sizes, may get rewritten once sizes are known if
            // stream is seekable
            _ApacheZipLong.ZIP64_MAGIC.putLong(buf, LFH_COMPRESSED_SIZE_OFFSET);
            _ApacheZipLong.ZIP64_MAGIC.putLong(buf, LFH_ORIGINAL_SIZE_OFFSET);
        } else if (zipMethod == DEFLATED || raf != null) {
            System.arraycopy(LZERO, 0, buf, LFH_COMPRESSED_SIZE_OFFSET, WORD);
            System.arraycopy(LZERO, 0, buf, LFH_ORIGINAL_SIZE_OFFSET, WORD);
        } else { // Stored
            putLong(ze.getSize(), buf, LFH_COMPRESSED_SIZE_OFFSET);
            putLong(ze.getSize(), buf, LFH_ORIGINAL_SIZE_OFFSET);
        }
        // file name length
        putShort(nameLen, buf, LFH_FILENAME_LENGTH_OFFSET);

        // extra field length
        putShort(extra.length, buf, LFH_EXTRA_LENGTH_OFFSET);

        // file name
        System.arraycopy(name.array(), name.arrayOffset(), buf,
                         LFH_FILENAME_OFFSET, nameLen);

        System.arraycopy(extra, 0, buf, LFH_FILENAME_OFFSET + nameLen, extra.length);
        return buf;
    }

    /**
     * Adds UnicodeExtra fields for name and file comment if mode is
     * ALWAYS or the data cannot be encoded using the configured
     * encoding.
     *
     * @param ze _ApacheZipEntry
     * @param encodable boolean
     * @param name ByteBuffer
     */
    private void addUnicodeExtraFields(_ApacheZipEntry ze, boolean encodable,
                                       ByteBuffer name)
        throws IOException {
        if (createUnicodeExtraFields == UnicodeExtraFieldPolicy.ALWAYS
            || !encodable) {
            ze.addExtraField(new _ApacheUnicodePathExtraField(ze.getName(),
                                                       name.array(),
                                                       name.arrayOffset(),
                                                       name.limit()
                                                       - name.position()));
        }

        String comm = ze.getComment();
        if (comm == null || comm.isEmpty()) {
            return;
        }

        if (createUnicodeExtraFields == UnicodeExtraFieldPolicy.ALWAYS
            || !zipEncoding.canEncode(comm)) {
            ByteBuffer commentB = getEntryEncoding(ze).encode(comm);
            ze.addExtraField(new _ApacheUnicodeCommentExtraField(comm,
                    commentB.array(), commentB.arrayOffset(),
                    commentB.limit() - commentB.position()));
        }
    }

    /**
     * Writes the data descriptor entry.
     *
     * @param ze the entry to write
     * @throws IOException on error
     * @since 1.1
     */
    protected void writeDataDescriptor(_ApacheZipEntry ze) throws IOException {
        if (ze.getMethod() != DEFLATED || raf != null) {
            return;
        }
        writeCounted(DD_SIG);
        writeCounted(_ApacheZipLong.getBytes(ze.getCrc()));
        if (!hasZip64Extra(ze)) {
            writeCounted(_ApacheZipLong.getBytes(ze.getCompressedSize()));
            writeCounted(_ApacheZipLong.getBytes(ze.getSize()));
        } else {
            writeCounted(_ApacheZipEightByteInteger.getBytes(ze.getCompressedSize()));
            writeCounted(_ApacheZipEightByteInteger.getBytes(ze.getSize()));
        }
    }

    /**
     * Writes the central file header entry.
     *
     * @param ze the entry to write
     * @throws IOException on error
     * @throws _ApacheZip64RequiredException if the archive's size exceeds 4
     * GByte and {@link _ApacheZip64Mode #setUseZip64} is {@link
     * _ApacheZip64Mode#Never}.
     */
    protected void writeCentralFileHeader(_ApacheZipEntry ze) throws IOException {
        byte[] centralFileHeader = createCentralFileHeader(ze);
        writeCounted(centralFileHeader);
    }

    private byte[] createCentralFileHeader(_ApacheZipEntry ze) throws IOException {
        final long lfhOffset = offsets.get(ze);
        final boolean needsZip64Extra = hasZip64Extra(ze)
                || ze.getCompressedSize() >= ZIP64_MAGIC
                || ze.getSize() >= ZIP64_MAGIC
                || lfhOffset >= ZIP64_MAGIC
                || zip64Mode == _ApacheZip64Mode.Always;

        if (needsZip64Extra && zip64Mode == _ApacheZip64Mode.Never) {
            // must be the offset that is too big, otherwise an
            // exception would have been throw in putArchiveEntry or
            // closeArchiveEntry
            throw new _ApacheZip64RequiredException(_ApacheZip64RequiredException
                    .ARCHIVE_TOO_BIG_MESSAGE);
        }


        handleZip64Extra(ze, lfhOffset, needsZip64Extra);

        return createCentralFileHeader(ze, getName(ze), lfhOffset, needsZip64Extra);
    }

    /**
     * Writes the central file header entry.
     *
     * @param ze the entry to write
     * @param name The encoded name
     * @param lfhOffset Local file header offset for this file
     * @throws IOException on error
     */
    private byte[] createCentralFileHeader(_ApacheZipEntry ze, ByteBuffer name, long lfhOffset,
                                           boolean needsZip64Extra) throws IOException {
        byte[] extra = ze.getCentralDirectoryExtra();

        // file comment length
        String comm = ze.getComment();
        if (comm == null) {
            comm = "";
        }

        ByteBuffer commentB = getEntryEncoding(ze).encode(comm);
        final int nameLen = name.limit() - name.position();
        final int commentLen = commentB.limit() - commentB.position();
        int len = CFH_FILENAME_OFFSET + nameLen + extra.length + commentLen;
        byte[] buf = new byte[len];

        System.arraycopy(CFH_SIG,  0, buf, CFH_SIG_OFFSET, WORD);

        // version made by
        // CheckStyle:MagicNumber OFF
        putShort((ze.getPlatform() << 8) | (!hasUsedZip64 ? DATA_DESCRIPTOR_MIN_VERSION : ZIP64_MIN_VERSION),
                buf, CFH_VERSION_MADE_BY_OFFSET);

        final int zipMethod = ze.getMethod();
        final boolean encodable = zipEncoding.canEncode(ze.getName());
        putShort(versionNeededToExtract(zipMethod, needsZip64Extra), buf, CFH_VERSION_NEEDED_OFFSET);
        getGeneralPurposeBits(zipMethod, !encodable && fallbackToUTF8).encode(buf, CFH_GPB_OFFSET);

        // compression method
        putShort(zipMethod, buf, CFH_METHOD_OFFSET);


        // last mod. time and date
        _ApacheZipUtil.toDosTime(calendarInstance, ze.getTime(), buf, CFH_TIME_OFFSET);

        // CRC
        // compressed length
        // uncompressed length
        putLong(ze.getCrc(), buf, CFH_CRC_OFFSET);
        if (ze.getCompressedSize() >= ZIP64_MAGIC
                || ze.getSize() >= ZIP64_MAGIC
                || zip64Mode == _ApacheZip64Mode.Always) {
            _ApacheZipLong.ZIP64_MAGIC.putLong(buf, CFH_COMPRESSED_SIZE_OFFSET);
            _ApacheZipLong.ZIP64_MAGIC.putLong(buf, CFH_ORIGINAL_SIZE_OFFSET);
        } else {
            putLong(ze.getCompressedSize(), buf, CFH_COMPRESSED_SIZE_OFFSET);
            putLong(ze.getSize(), buf, CFH_ORIGINAL_SIZE_OFFSET);
        }

        putShort(nameLen, buf, CFH_FILENAME_LENGTH_OFFSET);

        // extra field length
        putShort(extra.length, buf, CFH_EXTRA_LENGTH_OFFSET);

        putShort(commentLen, buf, CFH_COMMENT_LENGTH_OFFSET);

        // disk number start
        System.arraycopy(ZERO, 0, buf, CFH_DISK_NUMBER_OFFSET, SHORT);

        // internal file attributes
        putShort(ze.getInternalAttributes(), buf, CFH_INTERNAL_ATTRIBUTES_OFFSET);

        // external file attributes
        putLong(ze.getExternalAttributes(), buf, CFH_EXTERNAL_ATTRIBUTES_OFFSET);

        // relative offset of LFH
        if (lfhOffset >= ZIP64_MAGIC || zip64Mode == _ApacheZip64Mode.Always) {
            putLong(ZIP64_MAGIC, buf, CFH_LFH_OFFSET);
        } else {
            putLong(Math.min(lfhOffset, ZIP64_MAGIC), buf, CFH_LFH_OFFSET);
        }

        // file name
        System.arraycopy(name.array(), name.arrayOffset(), buf, CFH_FILENAME_OFFSET, nameLen);

        int extraStart = CFH_FILENAME_OFFSET + nameLen;
        System.arraycopy(extra, 0, buf, extraStart, extra.length);

        int commentStart = extraStart + extra.length;

        // file comment
        System.arraycopy(commentB.array(), commentB.arrayOffset(), buf, commentStart, commentLen);
        return buf;
    }

    /**
     * If the entry needs Zip64 extra information inside the central
     * directory then configure its data.
     *
     * @param ze _ApacheZipEntry
     * @param lfhOffset long
     * @param needsZip64Extra boolean
     */
    private void handleZip64Extra(_ApacheZipEntry ze, long lfhOffset,
                                  boolean needsZip64Extra) {
        if (needsZip64Extra) {
            _ApacheZip64ExtendedInformationExtraField z64 = getZip64Extra(ze);
            if (ze.getCompressedSize() >= ZIP64_MAGIC
                    || ze.getSize() >= ZIP64_MAGIC
                    || zip64Mode == _ApacheZip64Mode.Always) {
                z64.setCompressedSize(new _ApacheZipEightByteInteger(ze.getCompressedSize()));
                z64.setSize(new _ApacheZipEightByteInteger(ze.getSize()));
            } else {
                // reset value that may have been set for LFH
                z64.setCompressedSize(null);
                z64.setSize(null);
            }
            if (lfhOffset >= ZIP64_MAGIC || zip64Mode == _ApacheZip64Mode.Always) {
                z64.setRelativeHeaderOffset(new _ApacheZipEightByteInteger(lfhOffset));
            }
            ze.setExtra();
        }
    }

    /**
     * Writes the &quot;End of central dir record&quot;.
     *
     * @throws IOException on error
     * @throws _ApacheZip64RequiredException if the archive's size exceeds 4
     * GByte or there are more than 65535 entries inside the archive
     * and {@link _ApacheZip64Mode #setUseZip64} is {@link _ApacheZip64Mode#Never}.
     */
    protected void writeCentralDirectoryEnd() throws IOException {
        writeCounted(EOCD_SIG);

        // disk numbers
        writeCounted(ZERO);
        writeCounted(ZERO);

        // number of entries
        int numberOfEntries = entries.size();
        if (numberOfEntries > ZIP64_MAGIC_SHORT
            && zip64Mode == _ApacheZip64Mode.Never) {
            throw new _ApacheZip64RequiredException(_ApacheZip64RequiredException
                                             .TOO_MANY_ENTRIES_MESSAGE);
        }
        if (cdOffset > ZIP64_MAGIC && zip64Mode == _ApacheZip64Mode.Never) {
            throw new _ApacheZip64RequiredException(_ApacheZip64RequiredException
                                             .ARCHIVE_TOO_BIG_MESSAGE);
        }

        byte[] num = _ApacheZipShort.getBytes(Math.min(numberOfEntries,
                                                ZIP64_MAGIC_SHORT));
        writeCounted(num);
        writeCounted(num);

        // length and location of CD
        writeCounted(_ApacheZipLong.getBytes(Math.min(cdLength, ZIP64_MAGIC)));
        writeCounted(_ApacheZipLong.getBytes(Math.min(cdOffset, ZIP64_MAGIC)));

        // ZIP file comment
        ByteBuffer data = this.zipEncoding.encode(comment);
        int dataLen = data.limit() - data.position();
        writeCounted(_ApacheZipShort.getBytes(dataLen));
        writeCounted(data.array(), data.arrayOffset(), dataLen);
    }

    /**
     * Convert a Date object to a DOS date/time field.
     *
     * @param time the <code>Date</code> to convert
     * @return the date as a <code>_ApacheZipLong</code>
     * @since 1.1
     * @deprecated use _ApacheZipUtil#toDosTime
     */
    @Deprecated
    protected static _ApacheZipLong toDosTime(Date time) {
        return _ApacheZipUtil.toDosTime(time);
    }

    /**
     * Convert a Date object to a DOS date/time field.
     *
     * <p>Stolen from InfoZip's <code>fileio.c</code></p>
     *
     * @param t number of milliseconds since the epoch
     * @return the date as a byte array
     * @since 1.26
     * @deprecated use _ApacheZipUtil#toDosTime
     */
    @Deprecated
    protected static byte[] toDosTime(long t) {
        return _ApacheZipUtil.toDosTime(t);
    }

    /**
     * Retrieve the bytes for the given String in the encoding set for
     * this Stream.
     *
     * @param name the string to get bytes from
     * @return the bytes as a byte array
     * @throws ZipException on error
     *
     * @since 1.3
     */
    protected byte[] getBytes(String name) throws ZipException {
        try {
            ByteBuffer b =
                _ApacheZipEncodingHelper.getZipEncoding(encoding).encode(name);
            byte[] result = new byte[b.limit()];
            System.arraycopy(b.array(), b.arrayOffset(), result, 0,
                             result.length);
            return result;
        } catch (IOException ex) {
            throw new ZipException("Failed to encode name: " + ex.getMessage());
        }
    }

    /**
     * Writes the &quot;ZIP64 End of central dir record&quot; and
     * &quot;ZIP64 End of central dir locator&quot;.
     *
     * @throws IOException on error
     */
    protected void writeZip64CentralDirectory() throws IOException {
        if (zip64Mode == _ApacheZip64Mode.Never) {
            return;
        }

        if (!hasUsedZip64
            && (cdOffset >= ZIP64_MAGIC || cdLength >= ZIP64_MAGIC
                || entries.size() >= ZIP64_MAGIC_SHORT)) {
            // actually "will use"
            hasUsedZip64 = true;
        }

        if (!hasUsedZip64) {
            return;
        }

        long offset = written;

        writeOut(ZIP64_EOCD_SIG);
        // size, we don't have any variable length as we don't support
        // the extensible data sector, yet
        writeOut(_ApacheZipEightByteInteger
                 .getBytes(SHORT   /* version made by */
                           + SHORT /* version needed to extract */
                           + WORD  /* disk number */
                           + WORD  /* disk with central directory */
                           + DWORD /* number of entries in CD on this disk */
                           + DWORD /* total number of entries */
                           + DWORD /* size of CD */
                           + DWORD /* offset of CD */
                           ));

        // version made by and version needed to extract
        writeOut(_ApacheZipShort.getBytes(ZIP64_MIN_VERSION));
        writeOut(_ApacheZipShort.getBytes(ZIP64_MIN_VERSION));

        // disk numbers - four bytes this time
        writeOut(LZERO);
        writeOut(LZERO);

        // number of entries
        byte[] num = _ApacheZipEightByteInteger.getBytes(entries.size());
        writeOut(num);
        writeOut(num);

        // length and location of CD
        writeOut(_ApacheZipEightByteInteger.getBytes(cdLength));
        writeOut(_ApacheZipEightByteInteger.getBytes(cdOffset));

        // no "zip64 extensible data sector" for now

        // and now the "ZIP64 end of central directory locator"
        writeOut(ZIP64_EOCD_LOC_SIG);

        // disk number holding the ZIP64 EOCD record
        writeOut(LZERO);
        // relative offset of ZIP64 EOCD record
        writeOut(_ApacheZipEightByteInteger.getBytes(offset));
        // total number of disks
        writeOut(ONE);
    }

    /**
     * Write bytes to output or random access file.
     *
     * @param data the byte array to write
     * @throws IOException on error
     *
     * @since 1.14
     */
    protected final void writeOut(byte[] data) throws IOException {
        writeOut(data, 0, data.length);
    }

    /**
     * Write bytes to output or random access file.
     *
     * @param data the byte array to write
     * @param offset the start position to write from
     * @param length the number of bytes to write
     * @throws IOException on error
     *
     * @since 1.14
     */
    protected final void writeOut(byte[] data, int offset, int length)
        throws IOException {
        if (raf != null) {
            raf.write(data, offset, length);
        } else {
            out.write(data, offset, length);
        }
    }

    /**
     * Assumes a negative integer really is a positive integer that
     * has wrapped around and re-creates the original value.
     *
     * @param i the value to treat as unsigned int.
     * @return the unsigned int as a long.
     * @since 1.34
     * @deprecated use _ApacheZipUtil#adjustToLong
     */
    @Deprecated
    protected static long adjustToLong(int i) {
        return _ApacheZipUtil.adjustToLong(i);
    }

    private void deflateUntilInputIsNeeded() throws IOException {
        while (!def.needsInput()) {
            deflate();
        }
    }

    private _ApacheGeneralPurposeBit getGeneralPurposeBits(final int zipMethod, final boolean utfFallback) {
        _ApacheGeneralPurposeBit b = new _ApacheGeneralPurposeBit();
        b.useUTF8ForNames(useUTF8Flag || utfFallback);
        if (isDeflatedToOutputStream(zipMethod)) {
            b.useDataDescriptor(true);
        }
        return b;
    }

    private int versionNeededToExtract(final int zipMethod, final boolean zip64) {
        if (zip64) {
            return ZIP64_MIN_VERSION;
        }
        // requires version 2 as we are going to store length info
        // in the data descriptor
        return (isDeflatedToOutputStream(zipMethod))
            ? DATA_DESCRIPTOR_MIN_VERSION : INITIAL_VERSION;
    }

    private boolean isDeflatedToOutputStream(int zipMethod) {
        return zipMethod == DEFLATED && raf == null;
    }

    /**
     * Get the existing ZIP64 extended information extra field or
     * create a new one and add it to the entry.
     *
     * @param ze _ApacheZipEntry
     * @return _ApacheZip64ExtendedInformationExtraField
     */
    private _ApacheZip64ExtendedInformationExtraField getZip64Extra(_ApacheZipEntry ze) {
        if (entry != null) {
            entry.causedUseOfZip64 = !hasUsedZip64;
        }
        hasUsedZip64 = true;
        _ApacheZip64ExtendedInformationExtraField z64 =
            (_ApacheZip64ExtendedInformationExtraField)
            ze.getExtraField(_ApacheZip64ExtendedInformationExtraField
                             .HEADER_ID);
        if (z64 == null) {
            /*
              System.err.println("Adding z64 for " + ze.getName()
              + ", method: " + ze.getMethod()
              + " (" + (ze.getMethod() == STORED) + ")"
              + ", raf: " + (raf != null));
            */
            z64 = new _ApacheZip64ExtendedInformationExtraField();
        }

        // even if the field is there already, make sure it is the first one
        ze.addAsFirstExtraField(z64);

        return z64;
    }

    /**
     * Is there a ZIP64 extended information extra field for the
     * entry?
     *
     * @param ze _ApacheZipEntry
     * @return boolean
     */
    private boolean hasZip64Extra(_ApacheZipEntry ze) {
        return ze.getExtraField(_ApacheZip64ExtendedInformationExtraField
                                .HEADER_ID)
            != null;
    }

    /**
     * If the mode is AsNeeded and the entry is a compressed entry of
     * unknown size that gets written to a non-seekable stream the
     * change the default to Never.
     *
     * @param ze _ApacheZipEntry
     * @return _ApacheZip64Mode
     */
    private _ApacheZip64Mode getEffectiveZip64Mode(_ApacheZipEntry ze) {
        if (zip64Mode != _ApacheZip64Mode.AsNeeded
            || raf != null
            || ze.getMethod() != DEFLATED
            || ze.getSize() != -1) {
            return zip64Mode;
        }
        return _ApacheZip64Mode.Never;
    }

    private _ApacheZipEncoding getEntryEncoding(_ApacheZipEntry ze) {
        boolean encodable = zipEncoding.canEncode(ze.getName());
        return !encodable && fallbackToUTF8
            ? _ApacheZipEncodingHelper.UTF8_ZIP_ENCODING : zipEncoding;
    }

    private ByteBuffer getName(_ApacheZipEntry ze) throws IOException {
        return getEntryEncoding(ze).encode(ze.getName());
    }

    /**
     * Closes the underlying stream/file without finishing the
     * archive, the result will likely be a corrupt archive.
     *
     * <p>This method only exists to support tests that generate
     * corrupt archives so they can clean up any temporary files.</p>
     *
     * @throws IOException if close() fails
     */
    void destroy() throws IOException {
        if (raf != null) {
            raf.close();
        }
        if (out != null) {
            out.close();
        }
    }

    /**
     * enum that represents the possible policies for creating Unicode
     * extra fields.
     */
    public static final class UnicodeExtraFieldPolicy {
        /**
         * Always create Unicode extra fields.
         */
        public static final UnicodeExtraFieldPolicy ALWAYS =
            new UnicodeExtraFieldPolicy("always");
        /**
         * Never create Unicode extra fields.
         */
        public static final UnicodeExtraFieldPolicy NEVER =
            new UnicodeExtraFieldPolicy("never");
        /**
         * Create Unicode extra fields for filenames that cannot be
         * encoded using the specified encoding.
         */
        public static final UnicodeExtraFieldPolicy NOT_ENCODEABLE =
            new UnicodeExtraFieldPolicy("not encodeable");

        private final String name;
        private UnicodeExtraFieldPolicy(String n) {
            name = n;
        }
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Structure collecting information for the entry that is
     * currently being written.
     */
    private static final class CurrentEntry {
        private CurrentEntry(_ApacheZipEntry entry) {
            this.entry = entry;
        }
        /**
         * Current ZIP entry.
         */
        private final _ApacheZipEntry entry;
        /**
         * Offset for CRC entry in the local file header data for the
         * current entry starts here.
         */
        private long localDataStart = 0;
        /**
         * Data for local header data
         */
        private long dataStart = 0;
        /**
         * Number of bytes read for the current entry (can't rely on
         * Deflater#getBytesRead) when using DEFLATED.
         */
        private long bytesRead = 0;
        /**
         * Whether current entry was the first one using ZIP64 features.
         */
        private boolean causedUseOfZip64 = false;
        /**
         * Whether write() has been called at all.
         *
         * <p>In order to create a valid archive {@link
         * #closeEntry closeEntry} will write an empty
         * array to get the CRC right if nothing has been written to
         * the stream at all.</p>
         */
        private boolean hasWritten;
    }

}
