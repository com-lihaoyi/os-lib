package os

import java.nio.file.Files

object Internals{


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


  class Writable(val writeableData: geny.Generator[Array[Byte]])

  object Writable extends LowPri{
    implicit def WritableString(s: String) = new Writable(
      geny.Generator(s.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    )
    implicit def WritableBytes(a: Array[Byte]): Writable = new Writable(geny.Generator(a))

  }
  trait LowPri{

    implicit def WritableGenerator[M[_], T](a: M[T])
                                           (implicit f: T => Writable,
                                            i: M[T] => geny.Generator[T]) = {
      new Writable(
        i(a).flatMap(f(_).writeableData)
      )
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
