package os

import java.io.IOException
import java.nio.file
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import geny.Generator

/**
  * List the files and folders in a directory. Can be called with [[list.iter]]
  * to return an iterator. To list files recursively, use [[walk]]
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

/**
  * Walks a directory recursively and returns a [[IndexedSeq]] of all its contents.
  */
object walk {
  /**
    * @param path the root path whose contents you wish to walk
    *
    * @param skip Skip certain files or folders from appearing in the output.
    *             If you skip a folder, its entire subtree is ignored
    *
    * @param preOrder Whether you want a folder to appear before or after its
    *                 contents in the final sequence. e.g. if you're deleting
    *                 them recursively you want it to be false so the folder
    *                 gets deleted last, but if you're copying them recursively
    *                 you want `preOrder` to be `true` so the folder gets
    *                 created first.
    *
    * @param followLinks Whether or not to follow symlinks while walking; defaults
    *                    to false
    *
    * @param maxDepth The max depth of the tree you wish to walk; defaults to unlimited
    */
  def apply(path: Path,
            skip: Path => Boolean = _ => false,
            preOrder: Boolean = true,
            followLinks: Boolean = false,
            maxDepth: Int = Int.MaxValue): IndexedSeq[Path] = {
    iter(path, skip, preOrder, followLinks, maxDepth).toArray[Path]
  }

  /**
    * @param path the root path whose contents you wish to walk
    *
    * @param skip Skip certain files or folders from appearing in the output.
    *             If you skip a folder, its entire subtree is ignored
    *
    * @param preOrder Whether you want a folder to appear before or after its
    *                 contents in the final sequence. e.g. if you're deleting
    *                 them recursively you want it to be false so the folder
    *                 gets deleted last, but if you're copying them recursively
    *                 you want `preOrder` to be `true` so the folder gets
    *                 created first.
    *
    * @param followLinks Whether or not to follow symlinks while walking; defaults
    *                    to false
    *
    * @param maxDepth The max depth of the tree you wish to walk; defaults to unlimited
    */
  def attrs(path: Path,
            skip: Path => Boolean = _ => false,
            preOrder: Boolean = true,
            followLinks: Boolean = false,
            maxDepth: Int = Int.MaxValue): IndexedSeq[(Path, BasicFileAttributes)] = {
    iter.attrs(path, skip, preOrder, followLinks, maxDepth).toArray[(Path, BasicFileAttributes)]
  }

  object iter {

    /**
      * @param path the root path whose contents you wish to walk
      *
      * @param skip Skip certain files or folders from appearing in the output.
      *             If you skip a folder, its entire subtree is ignored
      *
      * @param preOrder Whether you want a folder to appear before or after its
      *                 contents in the final sequence. e.g. if you're deleting
      *                 them recursively you want it to be false so the folder
      *                 gets deleted last, but if you're copying them recursively
      *                 you want `preOrder` to be `true` so the folder gets
      *                 created first.
      *
      * @param followLinks Whether or not to follow symlinks while walking; defaults
      *                    to false
      *
      * @param maxDepth The max depth of the tree you wish to walk; defaults to unlimited
      */
    def apply(path: Path,
              skip: Path => Boolean = _ => false,
              preOrder: Boolean = true,
              followLinks: Boolean = false,
              maxDepth: Int = Int.MaxValue): Generator[Path] = {

      attrs(path, skip, preOrder, followLinks, maxDepth).map(_._1)
    }

    /**
      * @param path the root path whose contents you wish to walk
      *
      * @param skip Skip certain files or folders from appearing in the output.
      *             If you skip a folder, its entire subtree is ignored
      *
      * @param preOrder Whether you want a folder to appear before or after its
      *                 contents in the final sequence. e.g. if you're deleting
      *                 them recursively you want it to be false so the folder
      *                 gets deleted last, but if you're copying them recursively
      *                 you want `preOrder` to be `true` so the folder gets
      *                 created first.
      *
      * @param followLinks Whether or not to follow symlinks while walking; defaults
      *                    to false
      *
      * @param maxDepth The max depth of the tree you wish to walk; defaults to unlimited
      */
    def attrs(path: Path,
              skip: Path => Boolean = _ => false,
              preOrder: Boolean = true,
              followLinks: Boolean = false,
              maxDepth: Int = Int.MaxValue): Generator[(Path, BasicFileAttributes)] = {

      val opts0 = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
      val opts = new java.util.HashSet[FileVisitOption]
      if (followLinks) opts.add(FileVisitOption.FOLLOW_LINKS)
      val pNio = path.toNIO
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
                  if (preOrder && dirP != path) handleItem((dirP, attrs))
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
                    if (dirP != path) handleItem((dirP, attrsStack.remove(attrsStack.length - 1)))
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
