package os

import java.io._
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}

object zipIn {

  def apply(
             destination: os.Path,
             listOfPaths: List[os.Path],
             options: List[String] = List.empty
           ): os.Path = {
    val javaNIODestination: java.nio.file.Path = destination.toNIO
    val pathsToBeZipped: List[java.nio.file.Path] = listOfPaths.map(_.toNIO)

    val zipFilePath: java.nio.file.Path = resolveDestinationZipFile(javaNIODestination)

    // Determine if we need to append to the existing zip file
    if (options.contains("-u") && Files.exists(zipFilePath)) {
      // Append mode: Read existing entries, then add new entries
      appendToExistingZip(zipFilePath, pathsToBeZipped)
    } else {
      // Create a new zip file
      createNewZip(zipFilePath, pathsToBeZipped)
    }

    os.Path(zipFilePath) // Return the path to the created or updated zip file
  }

  private def createNewZip(zipFilePath: java.nio.file.Path, pathsToBeZipped: List[java.nio.file.Path]): Unit = {
    val zipOut = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile))
    try {
      pathsToBeZipped.foreach { path =>
        val file = path.toFile
        if (file.isDirectory) {
          zipFolder(file, file.getName, zipOut)
        } else {
          zipFile(file, zipOut)
        }
      }
    } finally {
      zipOut.close()
    }
  }

  private def appendToExistingZip(zipFilePath: java.nio.file.Path, pathsToBeZipped: List[java.nio.file.Path]): Unit = {
    // Temporary storage for the original zip entries
    val tempOut = new ByteArrayOutputStream()
    val zipOut = new ZipOutputStream(tempOut)

    val existingZip = new ZipFile(zipFilePath.toFile)

    // Copy existing entries
    existingZip.entries().asIterator().forEachRemaining { entry =>
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

    // Append new files and folders
    pathsToBeZipped.foreach { path =>
      val file = path.toFile
      if (file.isDirectory) {
        zipFolder(file, file.getName, zipOut)
      } else {
        zipFile(file, zipOut)
      }
    }

    zipOut.close()

    // Write the updated zip content back to the original zip file
    val newZipContent = tempOut.toByteArray
    Files.write(zipFilePath, newZipContent)
  }

  private def zipFile(file: java.io.File, zipOut: ZipOutputStream): Unit = {
    val fis = new FileInputStream(file)
    val zipEntry = new ZipEntry(file.getName)
    zipOut.putNextEntry(zipEntry)

    val buffer = new Array[Byte](1024)
    var length = fis.read(buffer)
    while (length >= 0) {
      zipOut.write(buffer, 0, length)
      length = fis.read(buffer)
    }

    fis.close()
  }

  private def zipFolder(folder: java.io.File, parentFolderName: String, zipOut: ZipOutputStream): Unit = {
    val files = folder.listFiles()
    if (files != null) {
      files.foreach { file =>
        if (file.isDirectory) {
          zipFolder(file, parentFolderName + "/" + file.getName, zipOut)
        } else {
          val fis = new FileInputStream(file)
          val zipEntry = new ZipEntry(parentFolderName + "/" + file.getName)
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

  private def resolveDestinationZipFile(destination: java.nio.file.Path): java.nio.file.Path = {
    // Check if destination is a directory or file
    val zipFilePath: java.nio.file.Path = if (Files.isDirectory(destination)) {
      destination.resolve("archive.zip") // Append default zip name
    } else {
      destination // Use provided file name
    }
    zipFilePath
  }
}

object unzip {

  def apply(source: os.Path, destination: Option[os.Path]): os.Path = {
    val sourcePath: java.nio.file.Path = source.toNIO

    // Ensure the source file is a zip file
    validateZipFile(sourcePath)

    // Determine the destination directory
    val destPath = destination match {
      case Some(dest) => createDestinationDirectory(dest)
      case None => sourcePath.getParent // Unzip in the same directory as the source
    }

    // Perform the unzip operation
    unzipFile(sourcePath, destPath)
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

  /** Unzips the file to the destination directory */
  private def unzipFile(sourcePath: java.nio.file.Path, destPath: java.nio.file.Path): Unit = {
    val zipInputStream = new ZipInputStream(new FileInputStream(sourcePath.toFile))

    try {
      var zipEntry: ZipEntry = zipInputStream.getNextEntry
      while (zipEntry != null) {
        val newFile = createFileForEntry(destPath, zipEntry)
        if (zipEntry.isDirectory) {
          // Create the directory
          Files.createDirectories(newFile.toPath)
        } else {
          // Extract the file
          extractFile(zipInputStream, newFile)
        }
        zipEntry = zipInputStream.getNextEntry
      }
    } finally {
      zipInputStream.closeEntry()
      zipInputStream.close()
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
}
