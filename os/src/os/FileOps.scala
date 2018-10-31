/**
 * Basic operations that take place on files. Intended to be
 * both light enough to use from the command line as well as
 * powerful and flexible enough to use in real applications to
 * perform filesystem operations
 */
package os

import java.io._
import java.nio.file
import java.nio.file._
import java.nio.file.attribute._

import geny.Generator

import scala.io.Codec
import scala.util.Try

/**
 * Makes directories up to the specified path. Equivalent
 * to `mkdir -p` in bash
 */
object makeDirs extends Function1[Path, Unit]{
  def apply(path: Path) = Files.createDirectories(path.toNIO)
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
    if (stat(from).isDir) walk(from).map(copyOne)
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
      require(target.segments.nonEmpty, s"Cannot rm a root directory: $target")

      val nioTarget = target.toNIO
      if (Files.exists(nioTarget)) {
        if (Files.isDirectory(nioTarget)) {
          walk.iter(target).foreach(remove)
        }
        Files.delete(nioTarget)
      }
    }
  }
}

/**
  * List the files and folders in a directory. Can be called with `.iter`
  * to return an iterator, or `.rec` to recursively list everything in
  * subdirectories. `.rec` is a [[Walker]] which means that apart from
  * straight-forwardly listing everything, you can pass in a `skip` predicate
  * to cause your recursion to skip certain files or folders.
  */
object list extends Internals.StreamableOp1[Path, Path, IndexedSeq[Path]] {
  def materialize(src: Path, i: geny.Generator[Path]) = i.toArray[Path].sorted

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
}


object walk extends Walker(){
  def apply(skip: Path => Boolean = _ => false,
            preOrder: Boolean = false,
            followLinks: Boolean = false,
            maxDepth: Int = Int.MaxValue) = Walker(skip, preOrder, followLinks, maxDepth)
}

/**
  * Walks a directory recursively and returns a [[IndexedSeq]] of all its contents.
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
                  preOrder: Boolean = false,
                  followLinks: Boolean = false,
                  maxDepth: Int = Int.MaxValue)
  extends Internals.StreamableOp1[Path, Path, IndexedSeq[Path]] {
  def attrs(arg: Path) = recursiveListFiles(arg)

  def materialize(src: Path, i: geny.Generator[Path]) = list.materialize(src, i)
  def recursiveListFiles(p: Path): geny.Generator[(Path, BasicFileAttributes)] = {
    val opts0 = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    val opts = new java.util.HashSet[FileVisitOption]
    if (followLinks) opts.add(FileVisitOption.FOLLOW_LINKS)
    val pNio = p.toNIO
    if (!Files.exists(pNio, opts0:_*)){
      throw new java.nio.file.NoSuchFileException(pNio.toString)
    }
    new geny.Generator[(Path, BasicFileAttributes)]{
      def generate(handleItem: ((Path, BasicFileAttributes)) => Generator.Action) = {
        var currentAction: geny.Generator.Action = geny.Generator.Continue
        val attrsStack = collection.mutable.Buffer.empty[BasicFileAttributes]
        def actionToResult(action: Generator.Action) = action match{
          case Generator.Continue => FileVisitResult.CONTINUE
          case Generator.End =>
            currentAction = Generator.End
            FileVisitResult.TERMINATE

        }
        Files.walkFileTree(
          pNio,
          opts,
          maxDepth,
          new FileVisitor[java.nio.file.Path]{
            def preVisitDirectory(dir: file.Path, attrs: BasicFileAttributes) = {
              val dirP = Path(dir.toAbsolutePath)
              if (skip(dirP)) FileVisitResult.SKIP_SUBTREE
              else actionToResult(
                if (preOrder && dirP != p) handleItem((dirP, attrs))
                else {
                  attrsStack.append(attrs)
                  currentAction
                }
              )
            }

            def visitFile(file: java.nio.file.Path, attrs: BasicFileAttributes) = {
              val fileP = Path(file.toAbsolutePath)
              actionToResult(
                if (skip(fileP)) currentAction
                else handleItem((fileP, attrs))
              )
            }

            def visitFileFailed(file: java.nio.file.Path, exc: IOException) =
              actionToResult(currentAction)

            def postVisitDirectory(dir: java.nio.file.Path, exc: IOException) = {
              actionToResult(
                if (preOrder) currentAction
                else {
                  val dirP = Path(dir.toAbsolutePath)
                  if (dirP != p) handleItem((dirP, attrsStack.remove(attrsStack.length - 1)))
                  else currentAction
                }
              )
            }
          }
        )
        currentAction
      }
    }
  }
  object iter extends (Path => geny.Generator[Path]){
    def apply(arg: Path) = recursiveListFiles(arg).map(_._1)
    def attrs(arg: Path) = recursiveListFiles(arg)
  }

}

/**
 * Write some data to a file. This can be a String, an Array[Byte], or a
 * Seq[String] which is treated as consecutive lines. By default, this
 * fails if a file already exists at the target location. Use [[write.over]]
 * or [[write.append]] if you want to over-write it or add to what's already
 * there.
 */
object write extends Function2[Path, Writable, Unit]{
  /**
    * Performs the actual opening and writing to a file. Basically cribbed
    * from `java.nio.file.Files.write` so we could re-use it properly for
    * different combinations of flags and all sorts of [[Writable]]s
    */
  def write(target: Path, data: Writable, flags: StandardOpenOption*) = {

    val out = Files.newOutputStream(target.toNIO, flags:_*)
    try data.write(out)
    finally if (out != null) out.close()
  }
  def apply(target: Path, data: Writable) = {
    makeDirs(target/RelPath.up)
    write(target, data, StandardOpenOption.CREATE_NEW)
  }

  /**
   * Identical to [[write]], except if the file already exists,
   * appends to the file instead of error-ing out
   */
  object append extends Function2[Path, Writable, Unit]{
    def apply(target: Path, data: Writable) = {
      makeDirs(target/RelPath.up)
      write(target, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
  }
  /**
   * Identical to [[write]], except if the file already exists,
   * replaces the file instead of error-ing out
   */
  object over extends Function2[Path, Writable, Unit]{
    def apply(target: Path, data: Writable) = {
      makeDirs(target/RelPath.up)
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

  def apply(arg: Readable) = apply(arg, java.nio.charset.StandardCharsets.UTF_8)
  def apply(arg: Readable, charSet: Codec) = {
    new String(read.bytes(arg), charSet.charSet)
  }

  object lines extends Internals.StreamableOp1[Readable, String, IndexedSeq[String]]{
    def materialize(src: Readable, i: geny.Generator[String]) = i.toArray[String]

    object iter extends (Readable => geny.Generator[String]){
      def apply(arg: Readable) = apply(arg, java.nio.charset.StandardCharsets.UTF_8)

      def apply(arg: Readable, charSet: Codec) = {
        new geny.Generator[String]{
          def generate(handleItem: String => Generator.Action) = {
            val is = arg.getInputStream()
            val isr = new InputStreamReader(is)
            val buf = new BufferedReader(isr)
            var currentAction: Generator.Action = Generator.Continue
            var looping = true
            try{
              while(looping){
                buf.readLine() match{
                  case null => looping = false
                  case s =>
                    handleItem(s) match{
                      case Generator.Continue => // go around again
                      case Generator.End =>
                        currentAction = Generator.End
                        looping = false
                    }
                }
              }
              currentAction
            } finally{
              is.close()
              isr.close()
              buf.close()
            }
          }
        }
      }
    }

    def apply(arg: Readable, charSet: Codec) = materialize(arg, iter(arg, charSet))
  }
  object bytes extends Function1[Readable, Array[Byte]]{
    def apply(arg: Readable) = {
      val is = arg.getInputStream
      val out = new java.io.ByteArrayOutputStream()
      val buffer = new Array[Byte](4096)
      var r = 0
      while (r != -1) {
        r = is.read(buffer)
        if (r != -1) out.write(buffer, 0, r)
      }
      is.close()
      out.toByteArray

    }
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

object getPerms {
  def apply(p: Path): PermSet = apply(p, followLinks = true)
  def apply(p: Path, followLinks: Boolean = true): PermSet = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    import collection.JavaConverters._
    new PermSet(Files.getPosixFilePermissions(p.toNIO, opts:_*).asScala.toSet)
  }
}

object setPerms {
  def apply(p: Path, arg2: PermSet) = {
    import collection.JavaConverters._
    Files.setPosixFilePermissions(p.toNIO, arg2.value.asJava)
  }
}

object getOwner {
  def apply(p: Path): UserPrincipal = apply(p, followLinks = true)
  def apply(p: Path, followLinks: Boolean = true): UserPrincipal = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.getOwner(p.toNIO, opts:_*)
  }
}

object setOwner {
  def apply(arg1: Path, arg2: UserPrincipal): Unit = Files.setOwner(arg1.toNIO, arg2)
  def apply(arg1: Path, arg2: String): Unit = {
    apply(
      arg1,
      arg1.root.getFileSystem.getUserPrincipalLookupService.lookupPrincipalByName(arg2)
    )
  }
}

object getGroup {
  def apply(p: Path): GroupPrincipal = apply(p, followLinks = true)
  def apply(p: Path, followLinks: Boolean = true): GroupPrincipal = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.getFileAttributeView(
      p.toNIO,
      classOf[PosixFileAttributeView],
      LinkOption.NOFOLLOW_LINKS
    ).readAttributes().group()
  }
}

object setGroup {
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
object isLink extends Function1[Path, Boolean]{
  def apply(p: Path) = Files.isSymbolicLink(p.toNIO)
}

/**
  * Checks whether the given path is a regular file
  */
object isFile extends Function1[Path, Boolean]{
  def apply(p: Path) = Files.isRegularFile(p.toNIO)
  def apply(p: Path, followLinks: Boolean = true) = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.isRegularFile(p.toNIO, opts:_*)
  }
}


/**
  * Checks whether the given path is a directory
  */
object isDir extends Function1[Path, Boolean]{
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


object stat extends Function1[os.Path, os.stat]{
  def apply(p: os.Path) = apply(p, followLinks = true)
  def apply(p: os.Path, followLinks: Boolean = true) = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    os.stat.make(
      // Don't blow up if we stat `root`
      p.segments.lastOption.getOrElse("/"),
      Files.readAttributes(
        p.toNIO,
        classOf[BasicFileAttributes],
        opts:_*
      ),
      Try(Files.readAttributes(
        p.toNIO,
        classOf[PosixFileAttributes],
        opts:_*
      )).toOption
    )
  }
  def make(name: String, attrs: BasicFileAttributes, posixAttrs: Option[PosixFileAttributes]) = {
    import collection.JavaConverters._
    new stat(
      name,
      attrs.size(),
      attrs.lastModifiedTime(),
      posixAttrs.map(_.owner).orNull,
      posixAttrs.map(a => new PermSet(a.permissions.asScala.toSet)).orNull,
      if (attrs.isRegularFile) FileType.File
      else if (attrs.isDirectory) FileType.Dir
      else if (attrs.isSymbolicLink) FileType.SymLink
      else if (attrs.isOther) FileType.Other
      else ???
    )
  }
  object full extends Function1[os.Path, os.stat.full] {
    def apply(p: os.Path) = apply(p, followLinks = true)
    def apply(p: os.Path, followLinks: Boolean = true) = {
      val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
      os.stat.full.make(
        p.segments.lastOption.getOrElse("/"),
        Files.readAttributes(
          p.toNIO,
          classOf[BasicFileAttributes],
          opts:_*
        ),
        Try(Files.readAttributes(
          p.toNIO,
          classOf[PosixFileAttributes],
          opts:_*
        )).toOption
      )
    }
    def make(name: String, attrs: BasicFileAttributes, posixAttrs: Option[PosixFileAttributes]) = {
      import collection.JavaConverters._
      new full(
        name,
        attrs.size(),
        attrs.lastModifiedTime(),
        attrs.lastAccessTime(),
        attrs.creationTime(),
        posixAttrs.map(_.group()).orNull,
        posixAttrs.map(_.owner()).orNull,
        posixAttrs.map(a => new PermSet(a.permissions.asScala.toSet)).orNull,
        if (attrs.isRegularFile) FileType.File
        else if (attrs.isDirectory) FileType.Dir
        else if (attrs.isSymbolicLink) FileType.SymLink
        else if (attrs.isOther) FileType.Other
        else ???
      )
    }
  }

  /**
    * A richer, more informative version of the [[stat]] object.
    *
    * Created using `stat.full! filePath`
    */
  case class full(name: String,
                  size: Long,
                  mtime: FileTime,
                  ctime: FileTime,
                  atime: FileTime,
                  group: GroupPrincipal,
                  owner: UserPrincipal,
                  permissions: PermSet,
                  fileType: FileType){
    override def productPrefix = "stat.full"
    def isDir = fileType == FileType.Dir
    def isSymLink = fileType == FileType.SymLink
    def isFile = fileType == FileType.File
  }
}

/**
  * The result from doing an system `stat` on a particular path.
  *
  * Created via `stat! filePath`.
  *
  * If you want more information, use `stat.full`
  */
case class stat(name: String,
                size: Long,
                mtime: FileTime,
                owner: UserPrincipal,
                permissions: PermSet,
                fileType: FileType){
  def isDir = fileType == FileType.Dir
  def isSymLink = fileType == FileType.SymLink
  def isFile = fileType == FileType.File
}
