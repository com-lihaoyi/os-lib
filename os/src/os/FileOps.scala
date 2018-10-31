/**
 * Basic operations that take place on files. Intended to be
 * both light enough to use from the command line as well as
 * powerful and flexible enough to use in real applications to
 * perform filesystem operations
 */
package os

import java.io.File
import java.nio.file._
import java.nio.file.attribute._

import scala.io.Codec


object Internals{


  trait Mover{
    def check: Boolean
    def apply(t: PartialFunction[String, String])(from: Path) = {
      if (check || t.isDefinedAt(from.last)){
        val dest = from/RelPath.up/t(from.last)
        new File(from.toString).renameTo(new File(dest.toString))
      }
    }
    def *(t: PartialFunction[Path, Path])(from: Path) = {
      if (check || t.isDefinedAt(from)) {
        val dest = t(from)
        makedirs(dest/RelPath.up)
        new File(from.toString).renameTo(new File(t(from).toString))
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


/**
 * Makes directories up to the specified path. Equivalent
 * to `mkdir -p` in bash
 */
object makedirs extends Function1[Path, Unit]{
  def apply(path: Path) = new File(path.toString).mkdirs()
}


trait CopyMove extends Function2[Path, Path, Unit]{

  /**
    * Copy or move a file into a particular folder, rather
    * than into a particular path
    */
  object into extends Function2[Path, Path, Unit]{
    def apply(from: Path, to: Path) = {
      CopyMove.this(from, to/from.last)
    }
  }

  /**
    * Copy or move a file, stomping over anything
    * that may have been there before
    */
  object over extends Function2[Path, Path, Unit]{
    def apply(from: Path, to: Path) = {
      remove(to)
      CopyMove.this(from, to)
    }
  }
}

/**
 * Moves a file or folder from one place to another.
 *
 * Creates any necessary directories
 */
object move extends Function2[Path, Path, Unit] with Internals.Mover with CopyMove{
  def apply(from: Path, to: Path) = {
    require(
      !to.startsWith(from),
      s"Can't move a directory into itself: $to is inside $from"
    )
    java.nio.file.Files.move(from.toNIO, to.toNIO)
  }


  def check = false

  object all extends Internals.Mover{
    def check = true
  }
}

/**
 * Copies a file or folder from one place to another.
 * Creates any necessary directories, and copies folders
 * recursively.
 */
object copy extends Function2[Path, Path, Unit] with CopyMove{
  def apply(from: Path, to: Path) = {
    require(
      !to.startsWith(from),
      s"Can't copy a directory into itself: $to is inside $from"
    )
    def copyOne(p: Path) = {
      Files.copy(p.toNIO, (to/(p relativeTo from)).toNIO)
    }

    copyOne(from)
    if (stat(from).isDir) list.rec(from).map(copyOne)
  }

}

/**
 * Roughly equivalent to bash's `rm -rf`. Deletes
 * any files or folders in the target path, or
 * does nothing if there aren't any
 */
object remove extends Function1[Path, Unit]{
  def apply(target: Path) = Files.delete(target.toNIO)

  object all extends Function1[Path, Unit]{
    def apply(target: Path) = {
      require(
        target.segments.nonEmpty,
        s"Cannot rm a root directory: $target"
      )
      // Emulate `rm -rf` functionality by ignoring non-existent files
      val files =
        try list.rec(target)
        catch {
          case e: NoSuchFileException => Nil
          case e: NotDirectoryException => Nil
        }

      files.toArray
        .reverseIterator
        .foreach(p => new File(p.toString).delete())
      new File(target.toString).delete
    }
  }
}

/**
 * A specialized Seq[Path] used to provide better a better pretty-printed
 * experience
 */
case class LsSeq(base: Path, listed: RelPath*) extends Seq[Path]{
  def length = listed.length
  def apply(idx: Int) = base/listed.apply(idx)
  def iterator = listed.iterator.map(base/)
}

trait ImplicitOp[V] extends Function1[Path, V]{
  /**
   * Make the common case of looking around the current directory fast by
   * letting the user omit the argument if there's one in scope
   */
  def !(implicit arg: Path): V = apply(arg)
}
/**
  * List the files and folders in a directory. Can be called with `.iter`
  * to return an iterator, or `.rec` to recursively list everything in
  * subdirectories. `.rec` is a [[list.Walker]] which means that apart from
  * straight-forwardly listing everything, you can pass in a `skip` predicate
  * to cause your recursion to skip certain files or folders.
  */
object list extends StreamableOp1[Path, Path, LsSeq] with ImplicitOp[LsSeq]{
  def materialize(src: Path, i: geny.Generator[Path]) =
    LsSeq(src, i.map(_ relativeTo src).toArray.sorted:_*)


  object iter extends (Path => geny.Generator[Path]){
    def apply(arg: Path) = geny.Generator.selfClosing{
        try {
          val dirStream = Files.newDirectoryStream(arg.toNIO)
          import collection.JavaConverters._
          (dirStream.iterator().asScala.map(Path(_)), () => dirStream.close())
        } catch {
          case _: AccessDeniedException => (Iterator[Path](), () => ())
        }
    }
  }

  object rec extends Walker(){
    def apply(skip: Path => Boolean = _ => false,
              preOrder: Boolean = false) = Walker(skip, preOrder)
  }

  /**
    * Walks a directory recursively and returns a [[LsSeq]] of all its contents.
    *
    * @param skip Skip certain files or folders from appearing in the output.
    *             If you skip a folder, its entire subtree is ignored
    * @param preOrder Whether you want a folder to appear before or after its
    *                 contents in the final sequence. e.g. if you're deleting
    *                 them recursively you want it to be false so the folder
    *                 gets deleted last, but if you're copying them recursively
    *                 you want `preOrder` to be `true` so the folder gets
    *                 created first.
    */
  case class Walker(skip: Path => Boolean = _ => false,
                    preOrder: Boolean = false)
  extends StreamableOp1[Path, Path, LsSeq] with ImplicitOp[LsSeq]{

    def materialize(src: Path, i: geny.Generator[Path]) = list.this.materialize(src, i)
    def recursiveListFiles(p: Path): geny.Generator[Path] = {
      def these = list.iter(p)
      implicit class withFilterable[T](x: geny.Generator[T]){
        def withFilter(p: T => Boolean) = x.filter(p)
      }
      for{
        thing <- these
        if !skip(thing)
        sub <- {
          if (!stat(thing).isDir) geny.Generator(thing)
          else{
            val children = recursiveListFiles(thing)
            if (preOrder) geny.Generator(thing) ++ children
            else children ++ geny.Generator(thing)
          }
        }
      } yield sub
    }
    object iter extends (Path => geny.Generator[Path]){
      def apply(arg: Path) = recursiveListFiles(arg)
    }

  }
}

/**
 * Write some data to a file. This can be a String, an Array[Byte], or a
 * Seq[String] which is treated as consecutive lines. By default, this
 * fails if a file already exists at the target location. Use [[write.over]]
 * or [[write.append]] if you want to over-write it or add to what's already
 * there.
 */
object write extends Function2[Path, Internals.Writable, Unit]{
  /**
    * Performs the actual opening and writing to a file. Basically cribbed
    * from `java.nio.file.Files.write` so we could re-use it properly for
    * different combinations of flags and all sorts of [[Internals.Writable]]s
    */
  def write(target: Path, data: Internals.Writable, flags: StandardOpenOption*) = {

    val out = Files.newOutputStream(target.toNIO, flags:_*)
    try {
      for(bytes <- data.writeableData){
        val len: Int = bytes.length
        var rem: Int = len
        while (rem > 0) {
          val n: Int = Math.min(rem, 8192)
          out.write(bytes, len - rem, n)
          rem -= n
        }
      }
    } finally {
      if (out != null) out.close()
    }

  }
  def apply(target: Path, data: Internals.Writable) = {
    makedirs(target/RelPath.up)
    write(target, data, StandardOpenOption.CREATE_NEW)
  }

  /**
   * Identical to [[write]], except if the file already exists,
   * appends to the file instead of error-ing out
   */
  object append extends Function2[Path, Internals.Writable, Unit]{
    def apply(target: Path, data: Internals.Writable) = {
      makedirs(target/RelPath.up)
      write(target, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
  }
  /**
   * Identical to [[write]], except if the file already exists,
   * replaces the file instead of error-ing out
   */
  object over extends Function2[Path, Internals.Writable, Unit]{
    def apply(target: Path, data: Internals.Writable) = {
      makedirs(target/RelPath.up)
      write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }
  }
}


/**
 * Reads a file into memory, either as a String,
 * as (read.lines(...): Seq[String]), or as (read.bytes(...): Array[Byte]).
 */
object read extends Function1[Readable, String]{
  def getInputStream(p: Readable) = p.getInputStream()

//  def apply(arg: InputPath) = new String(arg.getBytes, Charset.forName("UTF-8"))
  def apply(arg: Readable) = apply(arg, java.nio.charset.StandardCharsets.UTF_8)
  def apply(arg: Readable, charSet: Codec) = new String(arg.getBytes, charSet.charSet)

  object lines extends StreamableOp1[Readable, String, IndexedSeq[String]]{
    def materialize(src: Readable, i: geny.Generator[String]) = i.toArray[String]

    object iter extends (Readable => geny.Generator[String]){
      def apply(arg: Readable) = arg.getLineIterator(java.nio.charset.StandardCharsets.UTF_8)

      def apply(arg: Readable, charSet: Codec) = arg.getLineIterator(charSet)
    }

    def apply(arg: Readable, charSet: Codec) = arg.getLines(charSet)
    override def apply(arg: Readable) = apply(arg, java.nio.charset.StandardCharsets.UTF_8)
  }
  object bytes extends Function1[Readable, Array[Byte]]{
    def apply(arg: Readable) = arg.getBytes
  }
}

/**
 * Checks if a file or folder exists at the given path.
 */
object exists extends Function1[Path, Boolean]{
  def apply(p: Path) = Files.exists(p.toNIO)
  def apply(p: Path, followLinks: Boolean = true) = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.exists(p.toNIO, opts:_*)
  }
}

object chmod {
  def apply(p: Path, arg2: Set[PosixFilePermission]) = {
    import collection.JavaConverters._
    Files.setPosixFilePermissions(p.toNIO, arg2.asJava)
  }
  def apply(p: Path, arg2: String) = {
    require(
      arg2.length == 9,
      "Invalid permissions string: must be length 9, not " + arg2.length
    )
    import PosixFilePermission._
    val perms = new java.util.HashSet[PosixFilePermission]()
    def add(i: Int, expected: Char, perm: PosixFilePermission) = {
      if(arg2(i) == expected) perms.add(perm)
      else if (arg2(i) != '-') {
        throw new Exception(
          "Invalid permissions string: unknown character [" + arg2(i) + "] " +
          "at index " + i + ". Must be [-] or [" + expected + "]."
        )
      }
    }
    add(0, 'r', OWNER_READ)
    add(1, 'w', OWNER_WRITE)
    add(2, 'x', OWNER_EXECUTE)
    add(3, 'r', GROUP_READ)
    add(4, 'w', GROUP_WRITE)
    add(5, 'x', GROUP_EXECUTE)
    add(6, 'r', OTHERS_READ)
    add(7, 'w', OTHERS_WRITE)
    add(8, 'x', OTHERS_EXECUTE)
    Files.setPosixFilePermissions(p.toNIO, perms)
  }
  def apply(p: Path, arg2: Int) = {

    import PosixFilePermission._
    val perms = new java.util.HashSet[PosixFilePermission]()
    def add(i: Int, perm: PosixFilePermission) = {
      if((arg2 & (0x100 >> i)) != 0) perms.add(perm)
    }
    add(0, OWNER_READ)
    add(1, OWNER_WRITE)
    add(2, OWNER_EXECUTE)
    add(3, GROUP_READ)
    add(4, GROUP_WRITE)
    add(5, GROUP_EXECUTE)
    add(6, OTHERS_READ)
    add(7, OTHERS_WRITE)
    add(8, OTHERS_EXECUTE)
    Files.setPosixFilePermissions(p.toNIO, perms)
  }
}

object chown {
  def apply(arg1: Path, arg2: UserPrincipal): Unit = {
    Files.setOwner(arg1.toNIO, arg2)
  }
  def apply(arg1: Path, arg2: String): Unit = {
    apply(
      arg1,
      arg1.root.getFileSystem.getUserPrincipalLookupService.lookupPrincipalByName(arg2)
    )
  }
}
object chgrp {
  def apply(arg1: Path, arg2: GroupPrincipal): Unit = {
    Files.getFileAttributeView(
      arg1.toNIO,
      classOf[PosixFileAttributeView],
      LinkOption.NOFOLLOW_LINKS
    ).setGroup(arg2)
  }
  def apply(arg1: Path, arg2: String): Unit = {
    apply(
      arg1,
      arg1.root.getFileSystem.getUserPrincipalLookupService.lookupPrincipalByGroupName(arg2)
    )

  }
}

/**
 * Kills the given process with the given signal, e.g.
 * `kill(9)! pid`
 */
case class kill(signal: Int)(implicit wd: Path) extends Function1[Int, CommandResult]{
  def apply(pid: Int): CommandResult = {
    Shellout.%%('kill, "-" + signal, pid.toString)
  }
}

/**
  * Creates a hardlink between two paths
  */
object hardlink extends Function2[Path, Path, Unit]{
  def apply(src: Path, dest: Path) = {
    Files.createLink(dest.toNIO, src.toNIO)
  }
}

/**
  * Creates a symbolic link between two paths
  */
object symlink extends Function2[Path, Path, Unit]{
  def apply(src: Path, dest: Path) = {
    Files.createSymbolicLink(dest.toNIO, src.toNIO)
  }
}

/**
  * Checks whether the given path is a symbolic link
  */
object islink extends Function1[Path, Boolean]{
  def apply(p: Path) = Files.isSymbolicLink(p.toNIO)
}

/**
  * Checks whether the given path is a regular file
  */
object isfile extends Function1[Path, Boolean]{
  def apply(p: Path) = Files.isRegularFile(p.toNIO)
  def apply(p: Path, followLinks: Boolean = true) = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.isRegularFile(p.toNIO, opts:_*)
  }
}


/**
  * Checks whether the given path is a directory
  */
object isdir extends Function1[Path, Boolean]{
  def apply(p: Path) = Files.isDirectory(p.toNIO)
  def apply(p: Path, followLinks: Boolean = true) = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.isDirectory(p.toNIO, opts:_*)
  }
}

/**
  * Gets the size of the given file
  */
object size extends Function1[Path, Long]{
  def apply(p: Path) = Files.size(p.toNIO)
}

/**
  * Gets the mtime of the given file
  */
object mtime extends Function1[Path, Long]{
  def apply(p: Path) = Files.getLastModifiedTime(p.toNIO).toMillis
  def apply(p: Path, followLinks: Boolean = true) = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.getLastModifiedTime(p.toNIO, opts:_*).toMillis
  }
}

/*object free{
  def memory: Long = ???
  def disk: Long = ???
}
object process{
  def pid: Long = ???
  def pgrp: Long = ???
}
object system{
  object uname{
    def sysname: String = ???
    def nodename: String = ???
    def release: String = ???
    def version: String = ???
    def machine: String = ???
  }
}*/
