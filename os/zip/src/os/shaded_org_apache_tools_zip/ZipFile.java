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

import static os.shaded_org_apache_tools_zip.ZipConstants.DWORD;
import static os.shaded_org_apache_tools_zip.ZipConstants.SHORT;
import static os.shaded_org_apache_tools_zip.ZipConstants.WORD;
import static os.shaded_org_apache_tools_zip.ZipConstants.ZIP64_MAGIC;
import static os.shaded_org_apache_tools_zip.ZipConstants.ZIP64_MAGIC_SHORT;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

/**
 * Replacement for <code>java.util.ZipFile</code>.
 *
 * <p>This class adds support for file name encodings other than UTF-8
 * (which is required to work on ZIP files created by native zip tools
 * and is able to skip a preamble like the one found in self
 * extracting archives.  Furthermore it returns instances of
 * <code>os.shaded_org_apache_tools_zip.ZipEntry</code> instead of
 * <code>java.util.zip.ZipEntry</code>.</p>
 *
 * <p>It doesn't extend <code>java.util.zip.ZipFile</code> as it would
 * have to reimplement all methods anyway.  Like
 * <code>java.util.ZipFile</code>, it uses RandomAccessFile under the
 * covers and supports compressed and uncompressed entries.  As of
 * Apache Ant 1.9.0 it also transparently supports Zip64
 * extensions and thus individual entries and archives larger than 4
 * GB or with more than 65536 entries.</p>
 *
 * <p>The method signatures mimic the ones of
 * <code>java.util.zip.ZipFile</code>, with a couple of exceptions:
 *
 * <ul>
 *   <li>There is no getName method.</li>
 *   <li>entries has been renamed to getEntries.</li>
 *   <li>getEntries and getEntry return
 *   <code>os.shaded_org_apache_tools_zip.ZipEntry</code> instances.</li>
 *   <li>close is allowed to throw IOException.</li>
 * </ul>
 *
 */
class ZipFile implements Closeable {
    private static final int HASH_SIZE = 509;
    static final int NIBLET_MASK = 0x0f;
    static final int BYTE_SHIFT = 8;
    private static final int POS_0 = 0;
    private static final int POS_1 = 1;
    private static final int POS_2 = 2;
    private static final int POS_3 = 3;

    /**
     * List of entries in the order they appear inside the central
     * directory.
     */
    private final List<ZipEntry> entries = new LinkedList<>();

    /**
     * Maps String to list of ZipEntrys, name -> actual entries.
     */
    private final Map<String, LinkedList<ZipEntry>> nameMap =
        new HashMap<>(HASH_SIZE);

    private static final class OffsetEntry {
        private long headerOffset = -1;
        private long dataOffset = -1;
    }

    /**
     * The encoding to use for filenames and the file comment.
     *
     * <p>For a list of possible values see <a
     * href="https://docs.oracle.com/javase/8/docs/technotes/guides/intl/encoding.doc.html">
     * https://docs.oracle.com/javase/8/docs/technotes/guides/intl/encoding.doc.html</a>.
     * Defaults to the platform's default character encoding.</p>
     */
    private final String encoding;

    /**
     * The zip encoding to use for filenames and the file comment.
     */
    private final ZipEncoding zipEncoding;

    /**
     * File name of actual source.
     */
    private final String archiveName;

    /**
     * The actual data source.
     */
    private final RandomAccessFile archive;

    /**
     * Whether to look for and use Unicode extra fields.
     */
    private final boolean useUnicodeExtraFields;

    /**
     * Whether the file is closed.
     */
    private volatile boolean closed;

    // cached buffers
    private final byte[] DWORD_BUF = new byte[DWORD];
    private final byte[] WORD_BUF = new byte[WORD];
    private final byte[] CFH_BUF = new byte[CFH_LEN];
    private final byte[] SHORT_BUF = new byte[SHORT];

    /**
     * Opens the given file for reading, assuming the platform's
     * native encoding for file names.
     *
     * @param f the archive.
     *
     * @throws IOException if an error occurs while reading the file.
     */
    public ZipFile(final File f) throws IOException {
        this(f, null);
    }

    /**
     * Opens the given file for reading, assuming the platform's
     * native encoding for file names.
     *
     * @param name name of the archive.
     *
     * @throws IOException if an error occurs while reading the file.
     */
    public ZipFile(final String name) throws IOException {
        this(new File(name), null);
    }

    /**
     * Opens the given file for reading, assuming the specified
     * encoding for file names, scanning unicode extra fields.
     *
     * @param name name of the archive.
     * @param encoding the encoding to use for file names, use null
     * for the platform's default encoding
     *
     * @throws IOException if an error occurs while reading the file.
     */
    public ZipFile(final String name, final String encoding) throws IOException {
        this(new File(name), encoding, true);
    }

    /**
     * Opens the given file for reading, assuming the specified
     * encoding for file names and scanning for unicode extra fields.
     *
     * @param f the archive.
     * @param encoding the encoding to use for file names, use null
     * for the platform's default encoding
     *
     * @throws IOException if an error occurs while reading the file.
     */
    public ZipFile(final File f, final String encoding) throws IOException {
        this(f, encoding, true);
    }

    /**
     * Opens the given file for reading, assuming the specified
     * encoding for file names.
     *
     * @param f the archive.
     * @param encoding the encoding to use for file names, use null
     * for the platform's default encoding
     * @param useUnicodeExtraFields whether to use InfoZIP Unicode
     * Extra Fields (if present) to set the file names.
     *
     * @throws IOException if an error occurs while reading the file.
     */
    public ZipFile(final File f, final String encoding, final boolean useUnicodeExtraFields)
        throws IOException {
        this.archiveName = f.getAbsolutePath();
        this.encoding = encoding;
        this.zipEncoding = ZipEncodingHelper.getZipEncoding(encoding);
        this.useUnicodeExtraFields = useUnicodeExtraFields;
        archive = new RandomAccessFile(f, "r");
        boolean success = false;
        try {
            final Map<ZipEntry, NameAndComment> entriesWithoutUTF8Flag =
                populateFromCentralDirectory();
            resolveLocalFileHeaderData(entriesWithoutUTF8Flag);
            success = true;
        } finally {
            closed = !success;
            if (!success) {
                try {
                    archive.close();
                } catch (final IOException e2) {
                    // swallow, throw the original exception instead
                }
            }
        }
    }

    /**
     * The encoding to use for filenames and the file comment.
     *
     * @return null if using the platform's default character encoding.
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Closes the archive.
     * @throws IOException if an error occurs closing the archive.
     */
    @Override
    public void close() throws IOException {
        // this flag is only written here and read in finalize() which
        // can never be run in parallel.
        // no synchronization needed.
        closed = true;

        archive.close();
    }

    /**
     * close a zipfile quietly; throw no io fault, do nothing
     * on a null parameter
     * @param zipfile file to close, can be null
     */
    public static void closeQuietly(final ZipFile zipfile) {
        if (zipfile != null) {
            try {
                zipfile.close();
            } catch (final IOException e) {
                //ignore
            }
        }
    }

    /**
     * Returns all entries.
     *
     * <p>Entries will be returned in the same order they appear
     * within the archive's central directory.</p>
     *
     * @return all entries as {@link ZipEntry} instances
     */
    public Enumeration<ZipEntry> getEntries() {
        return Collections.enumeration(entries);
    }

    /**
     * Returns all entries in physical order.
     *
     * <p>Entries will be returned in the same order their contents
     * appear within the archive.</p>
     *
     * @return all entries as {@link ZipEntry} instances
     *
     * @since Ant 1.9.0
     */
    public Enumeration<ZipEntry> getEntriesInPhysicalOrder() {
        return entries.stream().sorted(OFFSET_COMPARATOR).collect(Collectors
            .collectingAndThen(Collectors.toList(), Collections::enumeration));
    }

    /**
     * Returns a named entry - or {@code null} if no entry by
     * that name exists.
     *
     * <p>If multiple entries with the same name exist the first entry
     * in the archive's central directory by that name is
     * returned.</p>
     *
     * @param name name of the entry.
     * @return the ZipEntry corresponding to the given name - or
     * {@code null} if not present.
     */
    public ZipEntry getEntry(final String name) {
        final LinkedList<ZipEntry> entriesOfThatName = nameMap.get(name);
        return entriesOfThatName != null ? entriesOfThatName.getFirst() : null;
    }

    /**
     * Returns all named entries in the same order they appear within
     * the archive's central directory.
     *
     * @param name name of the entry.
     * @return the Iterable&lt;ZipEntry&gt; corresponding to the
     * given name
     * @since 1.9.2
     */
    public Iterable<ZipEntry> getEntries(final String name) {
        final List<ZipEntry> entriesOfThatName = nameMap.get(name);
        return entriesOfThatName != null ? entriesOfThatName
            : Collections.emptyList();
    }

    /**
     * Returns all named entries in the same order their contents
     * appear within the archive.
     *
     * @param name name of the entry.
     * @return the Iterable&lt;ZipEntry&gt; corresponding to the
     * given name
     * @since 1.9.2
     */
    public Iterable<ZipEntry> getEntriesInPhysicalOrder(final String name) {
        if (nameMap.containsKey(name)) {
            return nameMap.get(name).stream().sorted(OFFSET_COMPARATOR)
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Whether this class is able to read the given entry.
     *
     * <p>May return false if it is set up to use encryption or a
     * compression method that hasn't been implemented yet.</p>
     *
     * @param ze ZipEntry
     * @return boolean
     */
    public boolean canReadEntryData(final ZipEntry ze) {
        return ZipUtil.canHandleEntryData(ze);
    }

    /**
     * Returns an InputStream for reading the contents of the given entry.
     *
     * @param ze the entry to get the stream for.
     * @return a stream to read the entry from.
     * @throws IOException if unable to create an input stream from the zipentry
     * @throws ZipException if the zipentry uses an unsupported feature
     */
    public InputStream getInputStream(final ZipEntry ze)
        throws IOException, ZipException {
        if (!(ze instanceof Entry)) {
            return null;
        }
        // cast validity is checked just above
        final OffsetEntry offsetEntry = ((Entry) ze).getOffsetEntry();
        ZipUtil.checkRequestedFeatures(ze);
        final long start = offsetEntry.dataOffset;
        // doesn't get closed if the method is not supported, but
        // doesn't hold any resources either
        final BoundedInputStream bis =
            new BoundedInputStream(start, ze.getCompressedSize()); //NOSONAR
        switch (ze.getMethod()) {
            case ZipEntry.STORED:
                return bis;
            case ZipEntry.DEFLATED:
                bis.addDummy();
                final Inflater inflater = new Inflater(true);
                return new InflaterInputStream(bis, inflater) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        inflater.end();
                    }
                };
            default:
                throw new ZipException("Found unsupported compression method "
                                       + ze.getMethod());
        }
    }

    public String getName() {
        return archiveName;
    }

    /**
     * Ensures that the close method of this zipfile is called when
     * there are no more references to it.
     * @see #close()
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            if (!closed) {
                System.err.printf("Cleaning up unclosed %s for archive %s%n",
                    getClass().getSimpleName(), archiveName);
                close();
            }
        } finally {
            super.finalize();
        }
    }

    /**
     * Length of a "central directory" entry structure without file
     * name, extra fields or comment.
     */
    private static final int CFH_LEN =
        /* version made by                 */ SHORT
        /* version needed to extract       */ + SHORT
        /* general purpose bit flag        */ + SHORT
        /* compression method              */ + SHORT
        /* last mod file time              */ + SHORT
        /* last mod file date              */ + SHORT
        /* crc-32                          */ + WORD
        /* compressed size                 */ + WORD
        /* uncompressed size               */ + WORD
        /* filename length                 */ + SHORT
        /* extra field length              */ + SHORT
        /* file comment length             */ + SHORT
        /* disk number start               */ + SHORT
        /* internal file attributes        */ + SHORT
        /* external file attributes        */ + WORD
        /* relative offset of local header */ + WORD;

    private static final long CFH_SIG =
        ZipLong.getValue(ZipOutputStream.CFH_SIG);

    /**
     * Reads the central directory of the given archive and populates
     * the internal tables with ZipEntry instances.
     *
     * <p>The ZipEntrys will know all data that can be obtained from
     * the central directory alone, but not the data that requires the
     * local file header or additional data to be read.</p>
     *
     * @return a map of zipentries that didn't have the language
     * encoding flag set when read.
     */
    private Map<ZipEntry, NameAndComment> populateFromCentralDirectory()
        throws IOException {
        final Map<ZipEntry, NameAndComment> noUTF8Flag = new HashMap<>();

        positionAtCentralDirectory();

        archive.readFully(WORD_BUF);
        long sig = ZipLong.getValue(WORD_BUF);

        if (sig != CFH_SIG && startsWithLocalFileHeader()) {
            throw new IOException(
                "central directory is empty, can't expand corrupt archive.");
        }

        while (sig == CFH_SIG) {
            readCentralDirectoryEntry(noUTF8Flag);
            archive.readFully(WORD_BUF);
            sig = ZipLong.getValue(WORD_BUF);
        }
        return noUTF8Flag;
    }

    /**
     * Reads an individual entry of the central directory, creates an
     * ZipEntry from it and adds it to the global maps.
     *
     * @param noUTF8Flag map used to collect entries that don't have
     * their UTF-8 flag set and whose name will be set by data read
     * from the local file header later.  The current entry may be
     * added to this map.
     */
    private void
        readCentralDirectoryEntry(final Map<ZipEntry, NameAndComment> noUTF8Flag)
        throws IOException {
        archive.readFully(CFH_BUF);
        int off = 0;
        final OffsetEntry offset = new OffsetEntry();
        final Entry ze = new Entry(offset);

        final int versionMadeBy = ZipShort.getValue(CFH_BUF, off);
        off += SHORT;
        ze.setPlatform((versionMadeBy >> BYTE_SHIFT) & NIBLET_MASK);

        off += SHORT; // skip version info

        final GeneralPurposeBit gpFlag = GeneralPurposeBit.parse(CFH_BUF, off);
        final boolean hasUTF8Flag = gpFlag.usesUTF8ForNames();
        final ZipEncoding entryEncoding =
            hasUTF8Flag ? ZipEncodingHelper.UTF8_ZIP_ENCODING : zipEncoding;
        ze.setGeneralPurposeBit(gpFlag);

        off += SHORT;

        ze.setMethod(ZipShort.getValue(CFH_BUF, off));
        off += SHORT;

        final long time = ZipUtil.dosToJavaTime(ZipLong.getValue(CFH_BUF, off));
        ze.setTime(time);
        off += WORD;

        ze.setCrc(ZipLong.getValue(CFH_BUF, off));
        off += WORD;

        ze.setCompressedSize(ZipLong.getValue(CFH_BUF, off));
        off += WORD;

        ze.setSize(ZipLong.getValue(CFH_BUF, off));
        off += WORD;

        final int fileNameLen = ZipShort.getValue(CFH_BUF, off);
        off += SHORT;

        final int extraLen = ZipShort.getValue(CFH_BUF, off);
        off += SHORT;

        final int commentLen = ZipShort.getValue(CFH_BUF, off);
        off += SHORT;

        final int diskStart = ZipShort.getValue(CFH_BUF, off);
        off += SHORT;

        ze.setInternalAttributes(ZipShort.getValue(CFH_BUF, off));
        off += SHORT;

        ze.setExternalAttributes(ZipLong.getValue(CFH_BUF, off));
        off += WORD;

        if (archive.length() - archive.getFilePointer() < fileNameLen) {
            throw new EOFException();
        }
        final byte[] fileName = new byte[fileNameLen];
        archive.readFully(fileName);
        ze.setName(entryEncoding.decode(fileName), fileName);

        // LFH offset,
        offset.headerOffset = ZipLong.getValue(CFH_BUF, off);
        // data offset will be filled later
        entries.add(ze);

        if (archive.length() - archive.getFilePointer() < extraLen) {
            throw new EOFException();
        }
        final byte[] cdExtraData = new byte[extraLen];
        archive.readFully(cdExtraData);
        ze.setCentralDirectoryExtra(cdExtraData);

        setSizesAndOffsetFromZip64Extra(ze, offset, diskStart);

        if (archive.length() - archive.getFilePointer() < commentLen) {
            throw new EOFException();
        }
        final byte[] comment = new byte[commentLen];
        archive.readFully(comment);
        ze.setComment(entryEncoding.decode(comment));

        if (!hasUTF8Flag && useUnicodeExtraFields) {
            noUTF8Flag.put(ze, new NameAndComment(fileName, comment));
        }
    }

    /**
     * If the entry holds a Zip64 extended information extra field,
     * read sizes from there if the entry's sizes are set to
     * 0xFFFFFFFFF, do the same for the offset of the local file
     * header.
     *
     * <p>Ensures the Zip64 extra either knows both compressed and
     * uncompressed size or neither of both as the internal logic in
     * ExtraFieldUtils forces the field to create local header data
     * even if they are never used - and here a field with only one
     * size would be invalid.</p>
     */
    private void setSizesAndOffsetFromZip64Extra(final ZipEntry ze,
                                                 final OffsetEntry offset,
                                                 final int diskStart)
        throws IOException {
        final Zip64ExtendedInformationExtraField z64 =
            (Zip64ExtendedInformationExtraField)
            ze.getExtraField(Zip64ExtendedInformationExtraField.HEADER_ID);
        if (z64 != null) {
            final boolean hasUncompressedSize = ze.getSize() == ZIP64_MAGIC;
            final boolean hasCompressedSize = ze.getCompressedSize() == ZIP64_MAGIC;
            final boolean hasRelativeHeaderOffset =
                offset.headerOffset == ZIP64_MAGIC;
            z64.reparseCentralDirectoryData(hasUncompressedSize,
                                            hasCompressedSize,
                                            hasRelativeHeaderOffset,
                                            diskStart == ZIP64_MAGIC_SHORT);

            if (hasUncompressedSize) {
                ze.setSize(z64.getSize().getLongValue());
            } else if (hasCompressedSize) {
                z64.setSize(new ZipEightByteInteger(ze.getSize()));
            }

            if (hasCompressedSize) {
                ze.setCompressedSize(z64.getCompressedSize().getLongValue());
            } else if (hasUncompressedSize) {
                z64.setCompressedSize(new ZipEightByteInteger(ze.getCompressedSize()));
            }

            if (hasRelativeHeaderOffset) {
                offset.headerOffset =
                    z64.getRelativeHeaderOffset().getLongValue();
            }
        }
    }

    /**
     * Length of the "End of central directory record" - which is
     * supposed to be the last structure of the archive - without file
     * comment.
     */
    private static final int MIN_EOCD_SIZE =
        /* end of central dir signature    */ WORD
        /* number of this disk             */ + SHORT
        /* number of the disk with the     */
        /* start of the central directory  */ + SHORT
        /* total number of entries in      */
        /* the central dir on this disk    */ + SHORT
        /* total number of entries in      */
        /* the central dir                 */ + SHORT
        /* size of the central directory   */ + WORD
        /* offset of start of central      */
        /* directory with respect to       */
        /* the starting disk number        */ + WORD
        /* zipfile comment length          */ + SHORT;

    /**
     * Maximum length of the "End of central directory record" with a
     * file comment.
     */
    private static final int MAX_EOCD_SIZE = MIN_EOCD_SIZE
        /* maximum length of zipfile comment */ + ZIP64_MAGIC_SHORT;

    /**
     * Offset of the field that holds the location of the first
     * central directory entry inside the "End of central directory
     * record" relative to the start of the "End of central directory
     * record".
     */
    private static final int CFD_LOCATOR_OFFSET =
        /* end of central dir signature    */ WORD
        /* number of this disk             */ + SHORT
        /* number of the disk with the     */
        /* start of the central directory  */ + SHORT
        /* total number of entries in      */
        /* the central dir on this disk    */ + SHORT
        /* total number of entries in      */
        /* the central dir                 */ + SHORT
        /* size of the central directory   */ + WORD;

    /**
     * Length of the "Zip64 end of central directory locator" - which
     * should be right in front of the "end of central directory
     * record" if one is present at all.
     */
    private static final int ZIP64_EOCDL_LENGTH =
        /* zip64 end of central dir locator sig */ WORD
        /* number of the disk with the start    */
        /* start of the zip64 end of            */
        /* central directory                    */ + WORD
        /* relative offset of the zip64         */
        /* end of central directory record      */ + DWORD
        /* total number of disks                */ + WORD;

    /**
     * Offset of the field that holds the location of the "Zip64 end
     * of central directory record" inside the "Zip64 end of central
     * directory locator" relative to the start of the "Zip64 end of
     * central directory locator".
     */
    private static final int ZIP64_EOCDL_LOCATOR_OFFSET =
        /* zip64 end of central dir locator sig */ WORD
        /* number of the disk with the start    */
        /* start of the zip64 end of            */
        /* central directory                    */ + WORD;

    /**
     * Offset of the field that holds the location of the first
     * central directory entry inside the "Zip64 end of central
     * directory record" relative to the start of the "Zip64 end of
     * central directory record".
     */
    private static final int ZIP64_EOCD_CFD_LOCATOR_OFFSET =
        /* zip64 end of central dir        */
        /* signature                       */ WORD
        /* size of zip64 end of central    */
        /* directory record                */ + DWORD
        /* version made by                 */ + SHORT
        /* version needed to extract       */ + SHORT
        /* number of this disk             */ + WORD
        /* number of the disk with the     */
        /* start of the central directory  */ + WORD
        /* total number of entries in the  */
        /* central directory on this disk  */ + DWORD
        /* total number of entries in the  */
        /* central directory               */ + DWORD
        /* size of the central directory   */ + DWORD;

    /**
     * Searches for either the &quot;Zip64 end of central directory
     * locator&quot; or the &quot;End of central dir record&quot;, parses
     * it and positions the stream at the first central directory
     * record.
     */
    private void positionAtCentralDirectory()
        throws IOException {
        positionAtEndOfCentralDirectoryRecord();
        boolean found = false;
        final boolean searchedForZip64EOCD =
            archive.getFilePointer() > ZIP64_EOCDL_LENGTH;
        if (searchedForZip64EOCD) {
            archive.seek(archive.getFilePointer() - ZIP64_EOCDL_LENGTH);
            archive.readFully(WORD_BUF);
            found = Arrays.equals(ZipOutputStream.ZIP64_EOCD_LOC_SIG, WORD_BUF);
        }
        if (!found) {
            // not a ZIP64 archive
            if (searchedForZip64EOCD) {
                skipBytes(ZIP64_EOCDL_LENGTH - WORD);
            }
            positionAtCentralDirectory32();
        } else {
            positionAtCentralDirectory64();
        }
    }

    /**
     * Parses the &quot;Zip64 end of central directory locator&quot;,
     * finds the &quot;Zip64 end of central directory record&quot; using the
     * parsed information, parses that and positions the stream at the
     * first central directory record.
     */
    private void positionAtCentralDirectory64()
        throws IOException {
        skipBytes(ZIP64_EOCDL_LOCATOR_OFFSET
                  - WORD /* signature has already been read */);
        archive.readFully(DWORD_BUF);
        archive.seek(ZipEightByteInteger.getLongValue(DWORD_BUF));
        archive.readFully(WORD_BUF);
        if (!Arrays.equals(WORD_BUF, ZipOutputStream.ZIP64_EOCD_SIG)) {
            throw new ZipException(
                "archive's ZIP64 end of central directory locator is corrupt.");
        }
        skipBytes(ZIP64_EOCD_CFD_LOCATOR_OFFSET
                  - WORD /* signature has already been read */);
        archive.readFully(DWORD_BUF);
        archive.seek(ZipEightByteInteger.getLongValue(DWORD_BUF));
    }

    /**
     * Searches for the &quot;End of central dir record&quot;, parses
     * it and positions the stream at the first central directory
     * record.
     */
    private void positionAtCentralDirectory32()
        throws IOException {
        skipBytes(CFD_LOCATOR_OFFSET);
        archive.readFully(WORD_BUF);
        archive.seek(ZipLong.getValue(WORD_BUF));
    }

    /**
     * Searches for the and positions the stream at the start of the
     * &quot;End of central dir record&quot;.
     */
    private void positionAtEndOfCentralDirectoryRecord()
        throws IOException {
        final boolean found = tryToLocateSignature(MIN_EOCD_SIZE, MAX_EOCD_SIZE,
                                             ZipOutputStream.EOCD_SIG);
        if (!found) {
            throw new ZipException("archive is not a ZIP archive");
        }
    }

    /**
     * Searches the archive backwards from minDistance to maxDistance
     * for the given signature, positions the RandomAccessFile right
     * at the signature if it has been found.
     */
    private boolean tryToLocateSignature(final long minDistanceFromEnd,
                                         final long maxDistanceFromEnd,
                                         final byte[] sig) throws IOException {
        boolean found = false;
        long off = archive.length() - minDistanceFromEnd;
        final long stopSearching =
            Math.max(0L, archive.length() - maxDistanceFromEnd);
        if (off >= 0) {
            for (; off >= stopSearching; off--) {
                archive.seek(off);
                int curr = archive.read();
                if (curr == -1) {
                    break;
                }
                if (curr == sig[POS_0]) {
                    curr = archive.read();
                    if (curr == sig[POS_1]) {
                        curr = archive.read();
                        if (curr == sig[POS_2]) {
                            curr = archive.read();
                            if (curr == sig[POS_3]) {
                                found = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (found) {
            archive.seek(off);
        }
        return found;
    }

    /**
     * Skips the given number of bytes or throws an EOFException if
     * skipping failed.
     */
    private void skipBytes(final int count) throws IOException {
        int totalSkipped = 0;
        while (totalSkipped < count) {
            final int skippedNow = archive.skipBytes(count - totalSkipped);
            if (skippedNow <= 0) {
                throw new EOFException();
            }
            totalSkipped += skippedNow;
        }
    }

    /**
     * Number of bytes in local file header up to the &quot;length of
     * filename&quot; entry.
     */
    private static final long LFH_OFFSET_FOR_FILENAME_LENGTH =
        /* local file header signature     */ WORD
        /* version needed to extract       */ + SHORT
        /* general purpose bit flag        */ + SHORT
        /* compression method              */ + SHORT
        /* last mod file time              */ + SHORT
        /* last mod file date              */ + SHORT
        /* crc-32                          */ + WORD
        /* compressed size                 */ + WORD
        /* uncompressed size               */ + WORD;

    /**
     * Walks through all recorded entries and adds the data available
     * from the local file header.
     *
     * <p>Also records the offsets for the data to read from the
     * entries.</p>
     */
    private void resolveLocalFileHeaderData(final Map<ZipEntry, NameAndComment>
                                            entriesWithoutUTF8Flag)
        throws IOException {
        for (ZipEntry zipEntry : entries) {
            // entries is filled in populateFromCentralDirectory and
            // never modified
            final Entry ze = (Entry) zipEntry;
            final OffsetEntry offsetEntry = ze.getOffsetEntry();
            final long offset = offsetEntry.headerOffset;
            archive.seek(offset + LFH_OFFSET_FOR_FILENAME_LENGTH);
            archive.readFully(SHORT_BUF);
            final int fileNameLen = ZipShort.getValue(SHORT_BUF);
            archive.readFully(SHORT_BUF);
            final int extraFieldLen = ZipShort.getValue(SHORT_BUF);
            int lenToSkip = fileNameLen;
            while (lenToSkip > 0) {
                final int skipped = archive.skipBytes(lenToSkip);
                if (skipped <= 0) {
                    throw new IOException(
                        "failed to skip file name in local file header");
                }
                lenToSkip -= skipped;
            }
            if (archive.length() - archive.getFilePointer() < extraFieldLen) {
                throw new EOFException();
            }
            final byte[] localExtraData = new byte[extraFieldLen];
            archive.readFully(localExtraData);
            try {
                ze.setExtra(localExtraData);
            } catch (RuntimeException ex) {
                final ZipException z = new ZipException("Invalid extra data in entry " + ze.getName());
                z.initCause(ex);
                throw z;
            }
            offsetEntry.dataOffset = offset + LFH_OFFSET_FOR_FILENAME_LENGTH
                + SHORT + SHORT + fileNameLen + extraFieldLen;

            if (entriesWithoutUTF8Flag.containsKey(ze)) {
                final NameAndComment nc = entriesWithoutUTF8Flag.get(ze);
                ZipUtil.setNameAndCommentFromExtraFields(ze, nc.name,
                                                         nc.comment);
            }

            final String name = ze.getName();
            LinkedList<ZipEntry> entriesOfThatName = nameMap.computeIfAbsent(name, k -> new LinkedList<>());
            entriesOfThatName.addLast(ze);
        }
    }

    /**
     * Checks whether the archive starts with a LFH.  If it doesn't,
     * it may be an empty archive.
     */
    private boolean startsWithLocalFileHeader() throws IOException {
        archive.seek(0);
        archive.readFully(WORD_BUF);
        return Arrays.equals(WORD_BUF, ZipOutputStream.LFH_SIG);
    }

    /**
     * InputStream that delegates requests to the underlying
     * RandomAccessFile, making sure that only bytes from a certain
     * range can be read.
     */
    private class BoundedInputStream extends InputStream {
        private long remaining;
        private long loc;
        private boolean addDummyByte = false;

        BoundedInputStream(final long start, final long remaining) {
            this.remaining = remaining;
            loc = start;
        }

        @Override
        public int read() throws IOException {
            if (remaining-- <= 0) {
                if (addDummyByte) {
                    addDummyByte = false;
                    return 0;
                }
                return -1;
            }
            synchronized (archive) {
                archive.seek(loc++);
                return archive.read();
            }
        }

        @Override
        public int read(final byte[] b, final int off, int len) throws IOException {
            if (remaining <= 0) {
                if (addDummyByte) {
                    addDummyByte = false;
                    b[off] = 0;
                    return 1;
                }
                return -1;
            }

            if (len <= 0) {
                return 0;
            }

            if (len > remaining) {
                len = (int) remaining;
            }
            int ret;
            synchronized (archive) {
                archive.seek(loc);
                ret = archive.read(b, off, len);
            }
            if (ret > 0) {
                loc += ret;
                remaining -= ret;
            }
            return ret;
        }

        /**
         * Inflater needs an extra dummy byte for nowrap - see
         * Inflater's javadocs.
         */
        void addDummy() {
            addDummyByte = true;
        }
    }

    private static final class NameAndComment {
        private final byte[] name;
        private final byte[] comment;
        private NameAndComment(final byte[] name, final byte[] comment) {
            this.name = name;
            this.comment = comment;
        }
    }

    /**
     * Compares two ZipEntries based on their offset within the archive.
     *
     * <p>Won't return any meaningful results if one of the entries
     * isn't part of the archive at all.</p>
     *
     * @since Ant 1.9.0
     */
    private final Comparator<ZipEntry> OFFSET_COMPARATOR = (e1, e2) -> {
        if (e1 == e2) {
            return 0;
        }

        final Entry ent1 = e1 instanceof Entry ? (Entry) e1 : null;
        final Entry ent2 = e2 instanceof Entry ? (Entry) e2 : null;
        if (ent1 == null) {
            return 1;
        }
        if (ent2 == null) {
            return -1;
        }
        final long val = (ent1.getOffsetEntry().headerOffset
                    - ent2.getOffsetEntry().headerOffset);
        return val == 0 ? 0 : val < 0 ? -1 : +1;
    };

    /**
     * Extends ZipEntry to store the offset within the archive.
     */
    private static class Entry extends ZipEntry {

        private final OffsetEntry offsetEntry;

        Entry(final OffsetEntry offset) {
            this.offsetEntry = offset;
        }

        OffsetEntry getOffsetEntry() {
            return offsetEntry;
        }

        @Override
        public int hashCode() {
            return 3 * super.hashCode()
                + (int) (offsetEntry.headerOffset % Integer.MAX_VALUE);
        }

        @Override
        public boolean equals(final Object other) {
            if (super.equals(other)) {
                // super.equals would return false if other were null or not an Entry
                final Entry otherEntry = (Entry) other;
                return offsetEntry.headerOffset
                        == otherEntry.offsetEntry.headerOffset //NOSONAR
                    && offsetEntry.dataOffset
                        == otherEntry.offsetEntry.dataOffset; //NOSONAR
            }
            return false;
        }
    }
}
