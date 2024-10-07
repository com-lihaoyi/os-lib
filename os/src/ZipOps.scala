package os


import java.net.URI
import java.nio.file.{FileSystem, FileSystems}
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import scala.util.matching.Regex

object zip {
  class ZipRoot(fs: FileSystem) extends Path(fs.getRootDirectories.iterator().next()) with AutoCloseable{
    def close(): Unit = fs.close()
  }

  def open(path: Path): ZipRoot = {
    new ZipRoot(FileSystems.newFileSystem(
      URI.create("jar:file:" + path.wrapped.toString),
      Map("create" -> "true").asJava
    ))
  }

  /**
   * Zips the provided list of files and directories into a single ZIP archive.
   *
   * @param destination      The path to the destination ZIP file.
   * @param sourcePaths      A list of paths to files and directories to be zipped. Defaults to an empty list.
   * @param excludePatterns  A list of regular expression patterns to exclude files from the ZIP archive. Defaults to an empty list.
   * @param includePatterns  A list of regular expression patterns to include files in the ZIP archive. Defaults to an empty list (includes all files).
   * @param preserveMtimes Whether to preserve modification times (mtimes) of the files.
   * @param preservePerms  Whether to preserve file permissions (POSIX).
   * @return The path to the created ZIP archive.
   */
  def apply(
             destination: os.Path,
             sourcePaths: List[os.Path] = List(),
             excludePatterns: List[String] = List(), // -x option
             includePatterns: List[String] = List(), // -i option
             preserveMtimes: Boolean = false,
             preservePerms: Boolean = true,
             appendToExisting: Option[os.Path] = None
  ): os.Path = {

    val appendToExistingSafe = appendToExisting.map{ existing =>
      if (existing != destination) existing
      else {
        val tmp = os.temp()
        os.copy.over(destination, tmp)
        tmp
      }
    }
    val f = new java.io.FileOutputStream(destination.toIO)
    try createNewZip(
      sourcePaths,
      excludePatterns.map(_.r),
      includePatterns.map(_.r),
      preserveMtimes,
      preservePerms,
      f,
      appendToExistingSafe
    ) finally f.close()

    destination
  }

  private def createNewZip(
                            sourcesToBeZipped: Seq[os.Path],
                            excludePatterns: Seq[Regex],
                            includePatterns: Seq[Regex],
                            preserveMtimes: Boolean,
                            preservePerms: Boolean,
                            out: java.io.OutputStream,
                            appendToExisting: Option[os.Path]
  ): Unit = {
    val zipOut = new ZipOutputStream(out)
    try {

      for(existing <- appendToExisting) {
        try {
          val readable = os.read.stream(existing)
          for(info <- os.unzip.streamRaw(readable)){
            makeZipEntry0(
              info.subpath,
              info.data,
              Option.when(preserveMtimes){info.modifiedTime},
              Option.when(preservePerms){info.permString},
              zipOut
            )
          }
        }
      }

      sourcesToBeZipped.foreach { source =>
        if (os.isDir(source)){

          for (path <- os.walk(source)) {
            if (os.isFile(path) && shouldInclude(path.toString, excludePatterns, includePatterns)) {
              makeZipEntry(
                path,
                path.subRelativeTo(source),
                preserveMtimes,
                preservePerms,
                zipOut
              )
            }
          }
        }else if (shouldInclude(source.last, excludePatterns, includePatterns)){
          makeZipEntry(
            source,
            os.sub / source.last,
            preserveMtimes,
            preservePerms,
            zipOut
          )
        }
      }
    } finally {
      zipOut.close()
    }
  }

  private def shouldInclude(
      fileName: String,
      excludePatterns: Seq[Regex],
      includePatterns: Seq[Regex]
  ): Boolean = {
    val isExcluded = excludePatterns.exists(_.findFirstIn(fileName).isDefined)
    val isIncluded =
      includePatterns.isEmpty || includePatterns.exists(_.findFirstIn(fileName).isDefined)
    !isExcluded && isIncluded
  }

  private def makeZipEntry(file: os.Path,
                           sub: os.SubPath,
                           preserveMtimes: Boolean,
                           preservePerms: Boolean,
                           zipOut: ZipOutputStream) = {
    val fis = Option.when(os.isFile(file)){os.read.inputStream(file)}
    try makeZipEntry0(
      sub,
      fis,
      Option.when(preserveMtimes){ os.mtime(file) },
      for (perms <- PlatformShims.readPermissions(file.toNIO) if preservePerms)  yield{
        PosixFilePermissions.toString(perms)
      },
      zipOut
    )
    finally fis.foreach(_.close())
  }

  def makeZipEntry0(sub: os.SubPath,
                    is: Option[java.io.InputStream],
                    preserveMtimes: Option[Long],
                    preservePerms: Option[String],
                    zipOut: ZipOutputStream) = {
    val zipEntry = new ZipEntry(sub.toString)

    for(mtime <- preserveMtimes) zipEntry.setTime(mtime)

    for(perms <- preservePerms)zipEntry.setComment(perms)

    zipOut.putNextEntry(zipEntry)
    is.foreach(_.transferTo(zipOut))
  }

  /**
   * Zips a folder recursively and returns a geny.Writable for streaming the ZIP data.
   *
   * @param source           The path to the folder to be zipped.
   * @param destination      The path to the destination ZIP file (optional). If not provided, a temporary ZIP file will be created.
   * @param appendToExisting Whether to append the listed paths to an existing ZIP file (if it exists). Defaults to false.
   * @param excludePatterns  A list of regular expression patterns to exclude files during zipping. Defaults to an empty list.
   * @param includePatterns  A list of regular expression patterns to include files in the ZIP archive. Defaults to an empty list (includes all files).
   * @param preserveMtimes   Whether to preserve modification times (mtimes) of the files.
   * @param preservePerms    Whether to preserve file permissions (POSIX).
   * @return A geny.Writable object for writing the ZIP data.
   */
  def stream(
      source: os.Path,
      excludePatterns: Seq[String] = List(),
      includePatterns: Seq[String] = List(),
      preserveMtimes: Boolean = false,
      preservePerms: Boolean = false,
      appendToExisting: Option[os.Path] = None
  ): geny.Writable = {
    (outputStream: java.io.OutputStream) => {
      createNewZip(
        Seq(source),
        excludePatterns.map(_.r),
        includePatterns.map(_.r),
        preserveMtimes,
        preservePerms,
        outputStream,
        appendToExisting
      )
    }
  }
}

object unzip {
  /**
   * Lists the contents of the given zip file without extracting it
   */
  def list(source: os.Path, excludePatterns: List[String] = List()): Iterator[os.SubPath] = {
    val zipFile = new ZipFile(source.toIO)
    for{
      entry <- zipFile.entries().asScala
      if !shouldExclude(entry.getName, excludePatterns.map(_.r))
    } yield os.SubPath(entry.getName)
  }

  /**
   * Extract the given zip file into the destination directory
   *
   * @param source          An `os.Path` containing a zip file
   * @param destination     The path to the destination directory for extracted files.
   * @param excludePatterns A list of regular expression patterns to exclude files during extraction. (Optional)
   */
  def apply(
      source: os.Path,
      destination: os.Path,
      excludePatterns: List[String] = List(), // Patterns to exclude
  ): os.Path = {
    stream(os.read.inputStream(source), destination, excludePatterns)
    destination
  }

  /** Determines if a file should be excluded based on the given patterns */
  private def shouldExclude(fileName: String, excludePatterns: List[Regex]): Boolean = {
    excludePatterns.exists(_.findFirstIn(fileName).isDefined)
  }

  /**
   * Unzips a ZIP data stream represented by a geny.Readable and extracts it to a destination directory.
   *
   * @param source          A geny.Readable object representing the ZIP data stream.
   * @param destination     The path to the destination directory for extracted files.
   * @param excludePatterns A list of regular expression patterns to exclude files during extraction. (Optional)
   */
  def stream(
      source: geny.Readable,
      destination: os.Path,
      excludePatterns: List[String] = List()
  ): Unit = {
    source.readBytesThrough { inputStream =>
      val zipInputStream = new ZipInputStream(inputStream)
      try {
        var zipEntry: ZipEntry = zipInputStream.getNextEntry
        while (zipEntry != null) {
          // Skip files that match the exclusion patterns
          if (!shouldExclude(zipEntry.getName, excludePatterns.map(_.r))) {
            val newFile = destination / os.SubPath(zipEntry.getName)
            if (zipEntry.isDirectory) os.makeDir.all(newFile)
            else {
              val outputStream = os.write.outputStream(newFile, createFolders = true)
              zipInputStream.transferTo(outputStream)
              outputStream.close()
            }
          }
          zipEntry = zipInputStream.getNextEntry
        }
      } finally {
        zipInputStream.closeEntry()
        zipInputStream.close()
      }
    }
  }
  trait StreamingZipEntryInfo{
    def subpath: os.SubPath
    def permString: String
    def modifiedTime: Long
    def data: Option[java.io.InputStream]
  }

  def streamRaw(source: geny.Readable, excludePatterns: List[String] = List()): geny.Generator[StreamingZipEntryInfo] = {
    new Generator[StreamingZipEntryInfo] {
      override def generate(handleItem: StreamingZipEntryInfo => Generator.Action): Generator.Action = {
        var lastAction: Generator.Action = Generator.Continue
        source.readBytesThrough { inputStream =>
          val zipInputStream = new ZipInputStream(inputStream)
          try {
            var zipEntry: ZipEntry = zipInputStream.getNextEntry
            println("zipEntry " + zipEntry)
            while (lastAction == Generator.Continue && zipEntry != null) {
              // Skip files that match the exclusion patterns
              if (!shouldExclude(zipEntry.getName, excludePatterns.map(_.r))) {

                val info = new StreamingZipEntryInfo{
                  val subpath = os.SubPath(zipEntry.getName)
                  val permString = zipEntry.getComment
                  val modifiedTime = zipEntry.getLastModifiedTime.toMillis
                  val data = Option.when(!zipEntry.isDirectory){ zipInputStream }
                }

                lastAction = handleItem(info)
              }
              zipEntry = zipInputStream.getNextEntry
            }
          } finally {
            zipInputStream.closeEntry()
            zipInputStream.close()
          }
        }
        lastAction
      }
    }
  }
}
