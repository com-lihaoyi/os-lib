package os

import java.io.IOException
import java.nio.file
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import geny.Generator

/**
  * Returns all the files and folders directly within the given folder. If the given
  * path is not a folder, raises an error. Can be called with [[list.iter]]
  * to return an iterator. To list files recursively, use [[walk]]
  */
object list extends Function1[Path, IndexedSeq[Path]] {
  def apply(src: Path) = iter(src).toArray[Path].sorted

  /**
    * Similar to [[os.list]]], except provides a [os.Generator](../../../readme.md#osgenerator) of
    * results rather than accumulating all of them in memory. Useful if the result set
    * is large.
    */
  object iter extends Function1[Path, geny.Generator[Path]]{
    def apply(arg: Path) = os.walk(arg, maxDepth = 1, followLinks = true)
  }
}

/**
  * Recursively walks the given folder and returns the paths of every file or folder
  * within.
  *
  * You can pass in a `skip` callback to skip files or folders you are not
  * interested in. This can avoid walking entire parts of the folder hierarchy,
  * saving time as compared to filtering them after the fact.
  *
  * By default, the paths are returned as a pre-order traversal: the enclosing
  * folder is occurs first before any of it's contents. You can pass in `preOrder =
  * false` to turn it into a post-order traversal, such that the enclosing folder
  * occurs last after all it's contents.
  *
  * `os.walk` returns but does not follow symlinks; pass in `followLinks = true` to
  * override that behavior. You can also specify a maximum depth you wish to walk
  * via the `maxDepth` parameter.
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
