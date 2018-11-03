package test.os

import utest.framework.TestPath
import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

object TestUtil {
  def prep[T](f: os.Path => T)(implicit tp: TestPath,
                               fn: sourcecode.FullName) ={
    val segments = Seq("out", "scratch") ++ fn.value.split('.').drop(2) ++ tp.value

    val directory = Paths.get(segments.mkString("/"))
    if (!Files.exists(directory)) Files.createDirectories(directory.getParent)
    else Files.walkFileTree(directory, new SimpleFileVisitor[Path]() {
      override def visitFile(file: Path, attrs: BasicFileAttributes) = {
        Files.delete(file)
        FileVisitResult.CONTINUE
      }

      override def postVisitDirectory(dir: Path, exc: IOException) = {
        Files.delete(dir)
        FileVisitResult.CONTINUE
      }
    })

    val original = Paths.get("os", "test", "resources", "test")
    Files.walkFileTree(original, new SimpleFileVisitor[Path]() {
      override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = {
        Files.copy(dir, directory.resolve(original.relativize(dir)), LinkOption.NOFOLLOW_LINKS)
        FileVisitResult.CONTINUE
      }

      override def visitFile(file: Path, attrs: BasicFileAttributes) = {
        Files.copy(file, directory.resolve(original.relativize(file)), LinkOption.NOFOLLOW_LINKS)
        FileVisitResult.CONTINUE
      }
    })

    f(os.Path(directory.toAbsolutePath))
  }
}
