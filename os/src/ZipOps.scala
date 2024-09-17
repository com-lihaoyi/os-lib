package os

import java.net.URI
import java.nio.file.{Path => _, _}
import collection.JavaConverters._


class zip(zipPath: Path) {

  private val fs = FileSystems.newFileSystem(
    URI.create("jar:file:" + zipPath.wrapped.toString),
    Map("create" -> "true").asJava)

  def path(path: Path): Path = new Path(fs.getPath(path.wrapped.toString))
  def root(): Path = path(os.root)
  def /(sub: PathChunk): Path = root() / sub
  def close(): Unit = fs.close()
}

object zip extends Function1[Path, zip] {
  def apply(path: Path): zip = new zip(path)
}
