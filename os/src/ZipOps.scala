package os

import geny.{Readable, Writable}

import java.io._
import java.nio.file.attribute.{
  BasicFileAttributeView,
  FileTime,
  PosixFileAttributeView,
  PosixFilePermissions
}
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import scala.util.matching.Regex

object zip {

  /**
   * Zips the provided list of files and directories into a single ZIP archive.
   *
   * @param destination      The path to the destination ZIP file.
   * @param listOfPaths      A list of paths to files and directories to be zipped. Defaults to an empty list.
   * @param appendToExisting Whether to append the listed paths to an existing ZIP file (if it exists). Defaults to false.
   * @param excludePatterns  A list of regular expression patterns to exclude files from the ZIP archive. Defaults to an empty list.
   * @param includePatterns  A list of regular expression patterns to include files in the ZIP archive. Defaults to an empty list (includes all files).
   * @param deletePatterns   A list of regular expression patterns to delete files from an existing ZIP archive before appending new ones. Defaults to an empty list.
   * @param preserveMtimes Whether to preserve modification times (mtimes) of the files.
   * @param preservePerms  Whether to preserve file permissions (POSIX).
   * @return The path to the created ZIP archive.
   */
  def apply(
      destination: os.Path,
      listOfPaths: List[os.Path] = List(),
      appendToExisting: Boolean = false,
      excludePatterns: List[String] = List(), // -x option
      includePatterns: List[String] = List(), // -i option
      deletePatterns: List[String] = List(), // -d option
      preserveMtimes: Boolean = true,
      preservePermissions: Boolean = true
  ): os.Path = {

    val javaNIODestination: java.nio.file.Path = destination.toNIO
    val pathsToBeZipped: List[java.nio.file.Path] = listOfPaths.map(_.toNIO)

    // Convert the string patterns into regex
    val excludeRegexPatterns: List[Regex] = excludePatterns.map(_.r)
    val includeRegexPatterns: List[Regex] = includePatterns.map(_.r)
    val deleteRegexPatterns: List[Regex] = deletePatterns.map(_.r)

    val zipFilePath: java.nio.file.Path = resolveDestinationZipFile(javaNIODestination)

    if (Files.exists(zipFilePath) && deletePatterns.nonEmpty) {
      deleteFilesFromZip(zipFilePath, deleteRegexPatterns)
    } else {
      if (appendToExisting && Files.exists(zipFilePath)) {
        appendToExistingZip(
          zipFilePath,
          pathsToBeZipped,
          excludeRegexPatterns,
          includeRegexPatterns,
          preserveMtimes,
          preservePermissions
        )
      } else {
        createNewZip(
          zipFilePath,
          pathsToBeZipped,
          excludeRegexPatterns,
          includeRegexPatterns,
          preserveMtimes,
          preservePermissions
        )
      }

    }
    os.Path(zipFilePath)
  }

  private def createNewZip(
      zipFilePath: java.nio.file.Path,
      pathsToBeZipped: List[java.nio.file.Path],
      excludePatterns: List[Regex],
      includePatterns: List[Regex],
      preserveMtimes: Boolean,
      preservePerms: Boolean
  ): Unit = {
    val zipOut = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile))
    try {
      pathsToBeZipped.foreach { path =>
        val file = path.toFile
        if (shouldInclude(file.getName, excludePatterns, includePatterns)) {
          if (file.isDirectory) {
            zipFolder(
              file,
              file.getName,
              zipOut,
              excludePatterns,
              includePatterns,
              preserveMtimes,
              preservePerms
            )
          } else {
            zipFile(file, zipOut, preserveMtimes, preservePerms)
          }
        }
      }
    } finally {
      zipOut.close()
    }
  }

  private def appendToExistingZip(
      zipFilePath: java.nio.file.Path,
      pathsToBeZipped: List[java.nio.file.Path],
      excludePatterns: List[Regex],
      includePatterns: List[Regex],
      preserveMtimes: Boolean,
      preservePerms: Boolean
  ): Unit = {
    val tempOut = new ByteArrayOutputStream()
    val zipOut = new ZipOutputStream(tempOut)

    val existingZip = new ZipFile(zipFilePath.toFile)

    existingZip.entries().asScala.foreach { entry =>
      if (shouldInclude(entry.getName, excludePatterns, includePatterns)) {
        val inputStream = existingZip.getInputStream(entry)
        zipOut.putNextEntry(new ZipEntry(entry.getName))

        val buffer = new Array[Byte](1024)
        var length = inputStream.read(buffer)
        while (length > 0) {
          zipOut.write(buffer, 0, length)
          length = inputStream.read(buffer)
        }
        inputStream.close()
        zipOut.closeEntry()
      }
    }

    pathsToBeZipped.foreach { path =>
      val file = path.toFile
      if (shouldInclude(file.getName, excludePatterns, includePatterns)) {
        if (file.isDirectory) {
          zipFolder(
            file,
            file.getName,
            zipOut,
            excludePatterns,
            includePatterns,
            preserveMtimes,
            preservePerms
          )
        } else {
          zipFile(file, zipOut, preserveMtimes, preservePerms)
        }
      }
    }

    zipOut.close()

    val newZipContent = tempOut.toByteArray
    Files.write(zipFilePath, newZipContent)
  }

  private def deleteFilesFromZip(
      zipFilePath: java.nio.file.Path,
      deletePatterns: List[Regex]
  ): Unit = {
    val tempOut = new ByteArrayOutputStream()
    val zipOut = new ZipOutputStream(tempOut)

    val existingZip = new ZipFile(zipFilePath.toFile)

    existingZip.entries().asScala.foreach { entry =>
      if (!deletePatterns.exists(_.findFirstIn(entry.getName).isDefined)) {
        val inputStream = existingZip.getInputStream(entry)
        zipOut.putNextEntry(new ZipEntry(entry.getName))

        val buffer = new Array[Byte](1024)
        var length = inputStream.read(buffer)
        while (length > 0) {
          zipOut.write(buffer, 0, length)
          length = inputStream.read(buffer)
        }
        inputStream.close()
        zipOut.closeEntry()
      }
    }

    zipOut.close()

    val newZipContent = tempOut.toByteArray
    Files.write(zipFilePath, newZipContent)
  }

  private def shouldInclude(
      fileName: String,
      excludePatterns: List[Regex],
      includePatterns: List[Regex]
  ): Boolean = {
    val isExcluded = excludePatterns.exists(_.findFirstIn(fileName).isDefined)
    val isIncluded =
      includePatterns.isEmpty || includePatterns.exists(_.findFirstIn(fileName).isDefined)
    !isExcluded && isIncluded
  }

  private def zipFile(
      file: java.io.File,
      zipOut: ZipOutputStream,
      preserveMtimes: Boolean,
      preservePerms: Boolean
  ): Unit = {
    val fis = new FileInputStream(file)
    val zipEntry = new ZipEntry(file.getName)

    // Preserve modification time if requested
    if (preserveMtimes) {
      zipEntry.setTime(file.lastModified())
    }

    // Set permissions if requested and supported
    val posixView = Files.getFileAttributeView(file.toPath, classOf[PosixFileAttributeView])
    if (preservePerms && posixView != null) {
      val attrs = posixView.readAttributes()
      val perms = attrs.permissions()
      zipEntry.setComment(PosixFilePermissions.toString(perms))
    }

    zipOut.putNextEntry(zipEntry)

    val buffer = new Array[Byte](1024)
    var length = fis.read(buffer)
    while (length >= 0) {
      zipOut.write(buffer, 0, length)
      length = fis.read(buffer)
    }

    fis.close()
  }

  private def zipFolder(
      folder: java.io.File,
      parentFolderName: String,
      zipOut: ZipOutputStream,
      excludePatterns: List[Regex],
      includePatterns: List[Regex],
      preserveMtimes: Boolean,
      preservePerms: Boolean
  ): Unit = {
    val files = folder.listFiles()
    if (files != null) {
      files.foreach { file =>
        if (shouldInclude(file.getName, excludePatterns, includePatterns)) {
          if (file.isDirectory) {
            zipFolder(
              file,
              parentFolderName + "/" + file.getName,
              zipOut,
              excludePatterns,
              includePatterns,
              preserveMtimes,
              preservePerms
            )
          } else {
            val fis = new FileInputStream(file)
            val zipEntry = new ZipEntry(parentFolderName + "/" + file.getName)

            // Preserve modification time if requested
            if (preserveMtimes) {
              zipEntry.setTime(file.lastModified())
            }

            // Set permissions if requested and supported
            val posixView = Files.getFileAttributeView(file.toPath, classOf[PosixFileAttributeView])
            if (preservePerms && posixView != null) {
              val attrs = posixView.readAttributes()
              val perms = attrs.permissions()
              zipEntry.setComment(PosixFilePermissions.toString(perms))
            }

            zipOut.putNextEntry(zipEntry)

            val buffer = new Array[Byte](1024)
            var length = fis.read(buffer)
            while (length >= 0) {
              zipOut.write(buffer, 0, length)
              length = fis.read(buffer)
            }

            fis.close()
          }
        }
      }
    }
  }

  private def resolveDestinationZipFile(destination: java.nio.file.Path): java.nio.file.Path = {
    val zipFilePath: java.nio.file.Path = if (Files.isDirectory(destination)) {
      destination.resolve("archive.zip")
    } else {
      destination
    }
    zipFilePath
  }

  /**
   * Zips a folder recursively and returns a geny.Writable for streaming the ZIP data.
   *
   * @param source           The path to the folder to be zipped.
   * @param destination      The path to the destination ZIP file (optional). If not provided, a temporary ZIP file will be created.
   * @param appendToExisting Whether to append the listed paths to an existing ZIP file (if it exists). Defaults to false.
   * @param excludePatterns  A list of regular expression patterns to exclude files during zipping. Defaults to an empty list.
   * @param includePatterns  A list of regular expression patterns to include files in the ZIP archive. Defaults to an empty list (includes all files).
   * @param deletePatterns   A list of regular expression patterns to delete files from an existing ZIP archive before appending new ones. Defaults to an empty list.
   * @param preserveMtimes   Whether to preserve modification times (mtimes) of the files.
   * @param preservePerms    Whether to preserve file permissions (POSIX).
   * @return A geny.Writable object for writing the ZIP data.
   */
  def stream(
      source: os.Path,
      destination: Option[os.Path] = None,
      appendToExisting: Boolean = false,
      excludePatterns: List[String] = List(),
      includePatterns: List[String] = List(),
      deletePatterns: List[String] = List(),
      preserveMtimes: Boolean = false, // Preserve modification times
      preservePerms: Boolean = false // Preserve POSIX permissions
  ): geny.Writable = {

    val zipOut = new ByteArrayOutputStream()
    val zipOutputStream = new ZipOutputStream(zipOut)

    try {
      val zipFilePath = destination.getOrElse {
        val tempFile = File.createTempFile(
          "temp-archive",
          ".zip",
          new File(System.getProperty("java.io.tmpdir"))
        )
        os.Path(tempFile)
      }
      val javaNIODestination: java.nio.file.Path = zipFilePath.toNIO
      val pathsToBeZipped = List(source.toNIO)

      // Convert the string patterns into regex
      val excludeRegexPatterns: List[Regex] = excludePatterns.map(_.r)
      val includeRegexPatterns: List[Regex] = includePatterns.map(_.r)
      val deleteRegexPatterns: List[Regex] = deletePatterns.map(_.r)

      if (Files.exists(javaNIODestination) && deletePatterns.nonEmpty) {
        deleteFilesFromZip(javaNIODestination, deleteRegexPatterns)
      } else {
        if (appendToExisting && Files.exists(javaNIODestination)) {
          appendToExistingZip(
            javaNIODestination,
            pathsToBeZipped,
            excludeRegexPatterns,
            includeRegexPatterns,
            preserveMtimes,
            preservePerms
          )
        } else {
          createNewZip(
            javaNIODestination,
            pathsToBeZipped,
            excludeRegexPatterns,
            includeRegexPatterns,
            preserveMtimes,
            preservePerms
          )
        }
      }
    } finally {
      zipOutputStream.close()
    }

    (outputStream: OutputStream) => {
      zipOut.writeTo(outputStream)
    }
  }
}

object unzip {

  /**
   * Unzips a ZIP archive to a specified destination directory.
   *
   * @param source The path to the ZIP archive.
   * @param destination (Optional) The path to the destination directory for extracted files.
   *                    If not provided, the source directory will be used.
   * @param excludePatterns (Optional) A list of regular expression patterns to exclude files from extraction.
   * @param listOnly (Optional) If `true`, lists the contents of the ZIP archive without extracting them.
   * @return The path to the destination directory where files are extracted, or the source path if `listOnly` is `true`.
   *
   * This function extracts the contents of the specified ZIP archive (`source`) to the given destination directory (`destination`).
   * It allows you to control which files are extracted by providing a list of regular expression patterns (`excludePatterns`)
   * to exclude specific files or folders. You can also use the `listOnly` flag to get a listing of the ZIP contents without actually extracting them.
   *
   * If no `destination` is provided, the ZIP archive will be extracted to the same directory as the source file.
   *
   * @throws java.io.IOException If there's an error while accessing or extracting the ZIP archive.
   */
  def apply(
      source: os.Path,
      destination: Option[os.Path] = None,
      excludePatterns: List[String] = List(), // Patterns to exclude
      listOnly: Boolean = false // List contents without extracting
  ): os.Path = {

    val sourcePath: java.nio.file.Path = source.toNIO

    // Ensure the source file is a zip file
    validateZipFile(sourcePath)

    // Convert the exclusion patterns to regex
    val excludeRegexPatterns: List[Regex] = excludePatterns.map(_.r)

    // If listOnly is true, list the contents and return the source path
    if (listOnly) {
      listContents(sourcePath, excludeRegexPatterns)
      return source
    }

    // Determine the destination directory
    val destPath = destination match {
      case Some(dest) => createDestinationDirectory(dest)
      case None => sourcePath.getParent // Unzip in the same directory as the source
    }

    // Perform the unzip operation
    unzipFile(sourcePath, destPath, excludeRegexPatterns)
    os.Path(destPath)
  }

  /** Validates if the input file is a valid zip file */
  private def validateZipFile(sourcePath: java.nio.file.Path): Unit = {
    if (!Files.exists(sourcePath)) {
      throw new IllegalArgumentException(s"Source file does not exist: $sourcePath")
    }
    if (!sourcePath.toString.endsWith(".zip")) {
      throw new IllegalArgumentException(s"Source file is not a zip file: $sourcePath")
    }
  }

  /** Creates the destination directory if it doesn't exist */
  private def createDestinationDirectory(destination: os.Path): java.nio.file.Path = {
    val destPath: java.nio.file.Path = destination.toNIO
    if (!Files.exists(destPath)) {
      Files.createDirectories(destPath) // Create directory if absent
    }
    destPath
  }

  private def unzipFile(
      sourcePath: java.nio.file.Path,
      destPath: java.nio.file.Path,
      excludePatterns: List[Regex]
  ): Unit = {

    val zipInputStream = new ZipInputStream(new FileInputStream(sourcePath.toFile))

    try {
      var zipEntry: ZipEntry = zipInputStream.getNextEntry
      while (zipEntry != null) {
        // Skip files that match the exclusion patterns
        if (!shouldExclude(zipEntry.getName, excludePatterns)) {
          val newFile = createFileForEntry(destPath, zipEntry)
          if (zipEntry.isDirectory) {
            Files.createDirectories(newFile.toPath)
          } else {
            extractFile(zipInputStream, newFile)
          }
        }
        zipEntry = zipInputStream.getNextEntry
      }
    } finally {
      zipInputStream.closeEntry()
      zipInputStream.close()
    }
  }

  /** Lists the contents of the zip file */
  private def listContents(sourcePath: java.nio.file.Path, excludePatterns: List[Regex]): Unit = {
    val zipFile = new ZipFile(sourcePath.toFile)

    zipFile.entries().asScala.foreach { entry =>
      if (!shouldExclude(entry.getName, excludePatterns)) {
        println(entry.getName)
      }
    }
  }

  /** Creates the file for the current ZipEntry, preserving the directory structure */
  private def createFileForEntry(destDir: java.nio.file.Path, zipEntry: ZipEntry): File = {
    val newFile = new File(destDir.toFile, zipEntry.getName)
    val destDirPath = destDir.toFile.getCanonicalPath
    val newFilePath = newFile.getCanonicalPath

    // Ensure that the file path is within the destination directory to avoid Zip Slip vulnerability
    if (!newFilePath.startsWith(destDirPath)) {
      throw new SecurityException(s"Entry is outside of the target directory: ${zipEntry.getName}")
    }
    newFile
  }

  /** Extracts the file content from the zip to the destination */
  private def extractFile(zipInputStream: ZipInputStream, file: File): Unit = {
    val parentDir = file.getParentFile
    if (!parentDir.exists()) {
      parentDir.mkdirs() // Ensure parent directories exist
    }

    val outputStream = Files.newOutputStream(file.toPath)
    val buffer = new Array[Byte](1024)
    var len = zipInputStream.read(buffer)
    while (len > 0) {
      outputStream.write(buffer, 0, len)
      len = zipInputStream.read(buffer)
    }
    outputStream.close()
  }

  /** Determines if a file should be excluded based on the given patterns */
  def shouldExclude(fileName: String, excludePatterns: List[Regex]): Boolean = {
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
            val newFile = createFileForEntry(destination.toNIO, zipEntry)
            if (zipEntry.isDirectory) {
              Files.createDirectories(newFile.toPath)
            } else {
              extractFile(zipInputStream, newFile)
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
}
