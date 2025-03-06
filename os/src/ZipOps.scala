package os

import java.net.URI
import java.nio.file.{FileSystem, FileSystems, Files}
import java.nio.file.attribute.{BasicFileAttributeView, FileTime, PosixFilePermission}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import scala.util.matching.Regex

object zip {

  /**
   * Opens a zip file as a filesystem root that you can operate on using `os.*` APIs. Note
   * that you need to call `close()` on the returned `ZipRoot` when you are done with it, to
   * avoid leaking filesystem resources
   */
  def open(path: Path): ZipRoot = {
    new ZipRoot(FileSystems.newFileSystem(
      new URI("jar", path.wrapped.toUri.toString, null),
      Map("create" -> "true").asJava
    ))
  }

  /**
   * Zips the provided list of files and directories into a single ZIP archive.
   *
   * If `dest` already exists and is a zip, performs modifications to `dest` in place
   * rather than creating a new zip.
   *
   * @param dest      The path to the destination ZIP file.
   * @param sources      A list of paths to files and directories to be zipped. Defaults to an empty list.
   * @param excludePatterns  A list of regular expression patterns to exclude files from the ZIP archive. Defaults to an empty list.
   * @param includePatterns  A list of regular expression patterns to include files in the ZIP archive. Defaults to an empty list (includes all files).
   * @param preserveMtimes Whether to preserve modification times (mtimes) of the files.
   * @param deletePatterns A list of regular expression patterns to delete files from an existing ZIP archive before appending new ones.
   * @param compressionLevel number from 0-9, where 0 is no compression and 9 is best compression. Defaults to -1 (default compression)
   * @return The path to the created ZIP archive.
   */
  def apply(
      dest: os.Path,
      sources: Seq[ZipSource] = List(),
      excludePatterns: Seq[Regex] = List(),
      includePatterns: Seq[Regex] = List(),
      preserveMtimes: Boolean = false,
      deletePatterns: Seq[Regex] = List(),
      compressionLevel: Int = java.util.zip.Deflater.DEFAULT_COMPRESSION
  ): os.Path = {
    checker.value.onWrite(dest)
    // check read preemptively in case "dest" is created
    for (source <- sources) checker.value.onRead(source.src)

    if (os.exists(dest)) {
      val opened = open(dest)
      try {
        for {
          openedPath <- os.walk(opened)
          if anyPatternsMatch(openedPath.relativeTo(opened).toString, deletePatterns)
        } os.remove.all(openedPath)

        createNewZip0(
          sources,
          excludePatterns,
          includePatterns,
          (path, sub) => {
            os.copy(path, opened / sub, createFolders = true)
            if (!preserveMtimes) {
              os.mtime.set(opened / sub, 0)
              // This is the only way we can properly zero out filesystem metadata within the
              // Zip file filesystem; `os.mtime.set` is not enough
              val view =
                Files.getFileAttributeView((opened / sub).toNIO, classOf[BasicFileAttributeView])
              view.setTimes(FileTime.fromMillis(0), FileTime.fromMillis(0), FileTime.fromMillis(0))
            }
          }
        )
      } finally opened.close()
    } else {
      val f = Files.newOutputStream(dest.toNIO)
      try createNewZip(
          sources,
          excludePatterns,
          includePatterns,
          preserveMtimes,
          compressionLevel,
          f
        )
      finally f.close()
      // preserve permissions
      if (!scala.util.Properties.isWin) {
        val zipFS = FileSystems.newFileSystem(
          new URI("jar", dest.wrapped.toUri.toString, null),
          Map(
            "create" -> "true",
            "enablePosixFileAttributes" -> "true",
            "defaultPermissions" -> Set.empty[String].asJava
          ).asJava
        )
        try {
          sources.foreach { source =>
            if (os.isDir(source.src)) {
              for (path <- os.walk(source.src)) {
                if (
                  os.isFile(path) && shouldInclude(path.toString, excludePatterns, includePatterns)
                ) {
                  val sourcePerms = os.perms(path)
                  val entry = zipFS.getPath(
                    (source.dest.getOrElse(os.sub) / path.subRelativeTo(source.src)).toString
                  )
                  Files.setPosixFilePermissions(entry, sourcePerms.toSet())
                }
              }
            } else if (shouldInclude(source.src.last, excludePatterns, includePatterns)) {
              val sourcePerms = os.perms(source.src)
              val entry = zipFS.getPath(source.dest.getOrElse(os.sub / source.src.last).toString)
              Files.setPosixFilePermissions(entry, sourcePerms.toSet())
            }
          }
        } finally {
          zipFS.close()
        }
      }
    }
    dest
  }

  private def createNewZip0(
      sources: Seq[ZipSource],
      excludePatterns: Seq[Regex],
      includePatterns: Seq[Regex],
      makeZipEntry0: (os.Path, os.SubPath) => Unit
  ): Unit = {
    sources.foreach { source =>
      if (os.isDir(source.src)) {
        for (path <- os.walk(source.src)) {
          if (os.isFile(path) && shouldInclude(path.toString, excludePatterns, includePatterns)) {
            makeZipEntry0(path, source.dest.getOrElse(os.sub) / path.subRelativeTo(source.src))
          }
        }
      } else if (shouldInclude(source.src.last, excludePatterns, includePatterns)) {
        makeZipEntry0(source.src, source.dest.getOrElse(os.sub / source.src.last))
      }
    }
  }
  private def createNewZip(
      sources: Seq[ZipSource],
      excludePatterns: Seq[Regex],
      includePatterns: Seq[Regex],
      preserveMtimes: Boolean,
      compressionLevel: Int,
      out: java.io.OutputStream
  ): Unit = {
    val zipOut = new ZipOutputStream(out)
    zipOut.setLevel(compressionLevel)

    try {
      createNewZip0(
        sources,
        excludePatterns,
        includePatterns,
        (path, sub) => makeZipEntry(path, sub, preserveMtimes, zipOut)
      )
    } finally {
      zipOut.close()
    }
  }

  private[os] def anyPatternsMatch(fileName: String, patterns: Seq[Regex]) = {
    patterns.exists(_.findFirstIn(fileName).isDefined)
  }
  private[os] def shouldInclude(
      fileName: String,
      excludePatterns: Seq[Regex],
      includePatterns: Seq[Regex]
  ): Boolean = {
    val isExcluded = anyPatternsMatch(fileName, excludePatterns)
    val isIncluded = includePatterns.isEmpty || anyPatternsMatch(fileName, includePatterns)
    !isExcluded && isIncluded
  }

  private def makeZipEntry(
      file: os.Path,
      sub: os.SubPath,
      preserveMtimes: Boolean,
      zipOut: ZipOutputStream
  ) = {

    val mtimeOpt = if (preserveMtimes) Some(os.mtime(file)) else None

    val fis = if (os.isFile(file)) Some(os.read.inputStream(file)) else None
    try makeZipEntry0(sub, fis, mtimeOpt, zipOut)
    finally fis.foreach(_.close())
  }

  private def makeZipEntry0(
      sub: os.SubPath,
      is: Option[java.io.InputStream],
      preserveMtimes: Option[Long],
      zipOut: ZipOutputStream
  ) = {
    val zipEntry = new ZipEntry(sub.toString)

    preserveMtimes match {
      case Some(mtime) => zipEntry.setTime(mtime)
      case None => zipEntry.setTime(0)
    }

    zipOut.putNextEntry(zipEntry)
    is.foreach(os.Internals.transfer(_, zipOut, close = false))
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
   * @return A geny.Writable object for writing the ZIP data.
   */
  def stream(
      sources: Seq[ZipSource],
      excludePatterns: Seq[Regex] = List(),
      includePatterns: Seq[Regex] = List(),
      preserveMtimes: Boolean = false,
      compressionLevel: Int = java.util.zip.Deflater.DEFAULT_COMPRESSION
  ): geny.Writable = {
    (outputStream: java.io.OutputStream) =>
      {
        createNewZip(
          sources,
          excludePatterns,
          includePatterns,
          preserveMtimes,
          compressionLevel,
          outputStream
        )
      }
  }

  /**
   * A filesystem root representing a zip file
   */
  class ZipRoot private[os] (fs: FileSystem) extends Path(fs.getRootDirectories.iterator().next())
      with AutoCloseable {
    def close(): Unit = fs.close()
  }

  /**
   * A file or folder you want to include in a zip file.
   */
  class ZipSource private[os] (val src: os.Path, val dest: Option[os.SubPath])
  object ZipSource {
    implicit def fromPath(src: os.Path): ZipSource = new ZipSource(src, None)
    implicit def fromSeqPath(srcs: Seq[os.Path]): Seq[ZipSource] = srcs.map(fromPath)
    implicit def fromPathTuple(tuple: (os.Path, os.SubPath)): ZipSource =
      new ZipSource(tuple._1, Some(tuple._2))
  }
}

object unzip {

  /**
   * Lists the contents of the given zip file without extracting it
   */
  def list(
      source: os.Path,
      excludePatterns: Seq[Regex] = List(),
      includePatterns: Seq[Regex] = List()
  ): Generator[os.SubPath] = {
    for {
      (zipEntry, zipInputStream) <-
        streamRaw(os.read.stream(source), excludePatterns, includePatterns)
    } yield os.SubPath(zipEntry.getName)
  }

  /**
   * Extract the given zip file into the destination directory
   *
   * @param source          An `os.Path` containing a zip file
   * @param dest     The path to the destination directory for extracted files.
   * @param excludePatterns A list of regular expression patterns to exclude files during extraction. (Optional)
   */
  def apply(
      source: os.Path,
      dest: os.Path,
      excludePatterns: Seq[Regex] = List(),
      includePatterns: Seq[Regex] = List()
  ): os.Path = {
    stream(os.read.stream(source), dest, excludePatterns, includePatterns)
    // preserve permissions
    if (!scala.util.Properties.isWin) {
      val zipFS = FileSystems.newFileSystem(
        new URI("jar", source.wrapped.toUri.toString, null),
        Map(
          "create" -> "true",
          "enablePosixFileAttributes" -> "true",
          "defaultPermissions" -> Set.empty[String].asJava
        ).asJava
      )
      try {
        os.walk(dest).foreach { path =>
          val relPath = path.subRelativeTo(dest).toString
          if (os.zip.shouldInclude(relPath, excludePatterns, includePatterns)) {
            val entry = zipFS.getPath(relPath)
            val permissions = Files.getAttribute(entry, "zip:permissions").asInstanceOf[java.util.Set[PosixFilePermission]]
            if (permissions != null) {
              os.perms.set(path, permissions)
            }
          }
        }
      } finally {
        zipFS.close()
      }
    }
    dest
  }

  /**
   * Unzips a ZIP data stream represented by a geny.Readable and extracts it to a destination directory.
   *
   * @param source          A geny.Readable object representing the ZIP data stream.
   * @param dest     The path to the destination directory for extracted files.
   * @param excludePatterns A list of regular expression patterns to exclude files during extraction. (Optional)
   */
  def stream(
      source: geny.Readable,
      dest: os.Path,
      excludePatterns: Seq[Regex] = List(),
      includePatterns: Seq[Regex] = List()
  ): Unit = {
    checker.value.onWrite(dest)
    for ((zipEntry, zipInputStream) <- streamRaw(source, excludePatterns, includePatterns)) {
      val newFile = dest / os.SubPath(zipEntry.getName)
      if (zipEntry.isDirectory) os.makeDir.all(newFile)
      else {
        val outputStream = os.write.outputStream(newFile, createFolders = true)
        os.Internals.transfer(zipInputStream, outputStream, close = false)
        outputStream.close()
      }
    }
  }

  /**
   * Low-level api that streams the contents of the given zip file: takes a `geny.Reaable`
   * providing the bytes of the zip file, and returns a `geny.Generator` containing `ZipEntry`s
   * and the underlying `ZipInputStream` representing the entries in the zip file.
   */
  def streamRaw(
      source: geny.Readable,
      excludePatterns: Seq[Regex] = List(),
      includePatterns: Seq[Regex] = List()
  ): geny.Generator[(ZipEntry, java.io.InputStream)] = {
    new Generator[(ZipEntry, java.io.InputStream)] {
      override def generate(handleItem: ((ZipEntry, java.io.InputStream)) => Generator.Action)
          : Generator.Action = {
        var lastAction: Generator.Action = Generator.Continue
        source.readBytesThrough { inputStream =>
          val zipInputStream = new ZipInputStream(inputStream)
          try {
            var zipEntry: ZipEntry = zipInputStream.getNextEntry
            while (lastAction == Generator.Continue && zipEntry != null) {
              // Skip files that match the exclusion patterns
              if (os.zip.shouldInclude(zipEntry.getName, excludePatterns, includePatterns)) {
                lastAction = handleItem((zipEntry, zipInputStream))
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
