package os

import java.io.{InputStream, OutputStream}
import java.nio.file.Files

object Internals{

  def transfer(src: InputStream, dest: OutputStream) = {
    val buffer = new Array[Byte](4096)
    var r = 0
    while (r != -1) {
      r = src.read(buffer)
      if (r != -1) dest.write(buffer, 0, r)
    }
    src.close()
  }
  trait Mover{
    def check: Boolean
    def apply(t: PartialFunction[String, String])(from: Path) = {
      if (check || t.isDefinedAt(from.last)){
        val dest = from/RelPath.up/t(from.last)
        Files.move(from.toNIO, dest.toNIO)
      }
    }
    def *(t: PartialFunction[Path, Path])(from: Path) = {
      if (check || t.isDefinedAt(from)) {
        val dest = t(from)
        makeDirs(dest/RelPath.up)
        Files.move(from.toNIO, t(from).toNIO)
      }
    }
  }

  /**
    * An [[Function1]] that returns a Seq[R], but can also do so
    * lazily (Iterator[R]) via `op.iter! arg`. You can then use
    * the iterator however you wish
    */
  trait StreamableOp1[T1, R, C <: Seq[R]] extends Function1[T1, C]{
    def materialize(src: T1, i: geny.Generator[R]): C
    def apply(arg: T1) = materialize(arg, iter(arg))

    /**
      * Returns a lazy [[Iterator]] instead of an eager sequence of results.
      */
    val iter: T1 => geny.Generator[R]
  }
}
