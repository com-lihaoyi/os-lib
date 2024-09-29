package os

import java.io.{FileInputStream, FileOutputStream}
import java.net.URI
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipOutputStream}

object zipIn {

  def apply(
             destination: os.Path,
             listOfPaths: List[os.Path],
             options: List[String] = List.empty
           ): os.Path = {
    val javaNIODestination: java.nio.file.Path = destination.toNIO
    val pathsToBeZipped: List[java.nio.file.Path] = listOfPaths.map(_.toNIO)

    val zipFilePath: java.nio.file.Path = resolveDestinationZipFile(javaNIODestination)

    // Create a zip output stream
    val zipOut = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile))

    try {
      pathsToBeZipped.foreach { path =>
        val file = path.toFile
        if (file.isDirectory) {
          // Zip the folder recursively
          zipFolder(file, file.getName, zipOut)
        } else {
          // Zip the individual file
          zipFile(file, zipOut)
        }
      }
    } finally {
      zipOut.close()
    }

    os.Path(zipFilePath) // Return the path to the created zip file
  }

  private def zipFile(file: java.io.File, zipOut: ZipOutputStream): Unit = {
    val fis = new FileInputStream(file)
    val zipEntry = new ZipEntry(file.getName)
    zipOut.putNextEntry(zipEntry)

    // Define a 1KB buffer for reading file content
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
          // Recursively zip subdirectory
          zipFolder(file, parentFolderName + "/" + file.getName, zipOut)
        } else {
          // Add the file with its relative path
          val fis = new FileInputStream(file)
          val zipEntry = new ZipEntry(parentFolderName + "/" + file.getName)
          zipOut.putNextEntry(zipEntry)

          // Write file content to zip
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
