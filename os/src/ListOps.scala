package os

import java.io.IOException
import java.nio.file
import java.nio.file.{Path => _, _}
import java.nio.file.attribute.BasicFileAttributes

/**
  * Returns all the files and folders directly within the given folder. If the
  * given path is not a folder, raises an error. Can be called with
  * [[list.stream]] to return an iterator. To list files recursively, use
  * [[walk]]
  *
  * For convenience `os.list` sorts the entries in the folder before returning
  * them. You can disable sorted by passing in the flag `sort = false`.
  */
object list extends Function1[Path, IndexedSeq[Path]] {
  def apply(src: Path, sort: Boolean = true) = {
    val arr = stream(src).toArray[Path]
    if (sort) arr.sorted
    else arr
  }
  def apply(src: Path) = apply(src, true)

  /**
    * Similar to [[os.list]]], except provides a [[os.Generator]] of results
    * rather than accumulating all of them in memory. Useful if the result set
    * is large.
    */
  object stream extends Function1[Path, geny.Generator[Path]]{
    def apply(arg: Path) = new Generator[Path] {
      def generate(handleItem: Path => Generator.Action) = {
        val ds = Files.newDirectoryStream(arg.toNIO)
        val iter = ds.iterator()
        var currentAction: Generator.Action = Generator.Continue
        try {
          while (iter.hasNext && currentAction == Generator.Continue){
            currentAction = handleItem(Path(iter.next().toAbsolutePath))
          }
        } finally ds.close()
        currentAction
      }
    }
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
    *
    * @param includeTarget Whether or not to include the given path as part of the walk.
    *                      If `true`, does not raise an error if the given path is a
    *                      simple file and not a folder
    */
  def apply(path: Path,
            skip: Path => Boolean = _ => false,
            preOrder: Boolean = true,
            followLinks: Boolean = false,
            maxDepth: Int = Int.MaxValue,
            includeTarget: Boolean = false): IndexedSeq[Path] = {
    stream(path, skip, preOrder, followLinks, maxDepth, includeTarget).toArray[Path]
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
    *
    * @param includeTarget Whether or not to include the given path as part of the walk.
    *                      If `true`, does not raise an error if the given path is a
    *                      simple file and not a folder
    */
  def attrs(path: Path,
            skip: (Path, os.StatInfo) => Boolean = (_, _) => false,
            preOrder: Boolean = true,
            followLinks: Boolean = false,
            maxDepth: Int = Int.MaxValue,
            includeTarget: Boolean = false): IndexedSeq[(Path, os.StatInfo)] = {
    stream.attrs(path, skip, preOrder, followLinks, maxDepth, includeTarget)
      .toArray[(Path, os.StatInfo)]
  }

  object stream {

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
      *
      * @param includeTarget Whether or not to include the given path as part of the walk.
      *                      If `true`, does not raise an error if the given path is a
      *                      simple file and not a folder
      */
    def apply(path: Path,
              skip: Path => Boolean = _ => false,
              preOrder: Boolean = true,
              followLinks: Boolean = false,
              maxDepth: Int = Int.MaxValue,
              includeTarget: Boolean = false): Generator[Path] = {

      attrs(path, (p, _) => skip(p), preOrder, followLinks, maxDepth, includeTarget).map(_._1)
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
      *
      * @param includeTarget Whether or not to include the given path as part of the walk.
      *                      If `true`, does not raise an error if the given path is a
      *                      simple file and not a folder
      */
    def attrs(path: Path,
              skip: (Path, os.StatInfo) => Boolean = (_, _) => false,
              preOrder: Boolean = true,
              followLinks: Boolean = false,
              maxDepth: Int = Int.MaxValue,
              includeTarget: Boolean = false): Generator[(Path, os.StatInfo)] = {

      val opts0 = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
      val opts = new java.util.HashSet[FileVisitOption]
      if (followLinks) opts.add(FileVisitOption.FOLLOW_LINKS)
      val pathNIO = path.wrapped
      if (!Files.exists(pathNIO, opts0:_*)){
        throw new java.nio.file.NoSuchFileException(pathNIO.toString)
      }
      new geny.Generator[(Path, os.StatInfo)]{
        def generate(handleItem: ((Path, os.StatInfo)) => Generator.Action) = {
          var currentAction: geny.Generator.Action = geny.Generator.Continue
          val attrsStack = collection.mutable.Buffer.empty[os.StatInfo]
          def actionToResult(action: Generator.Action) = action match{
            case Generator.Continue => FileVisitResult.CONTINUE
            case Generator.End =>
              currentAction = Generator.End
              FileVisitResult.TERMINATE
          }
          if (includeTarget && preOrder) handleItem(
            (
              path,
              os.StatInfo.make(
                java.nio.file.Files.readAttributes(path.toNIO, classOf[BasicFileAttributes])
              )
            )
          )

          if (os.isDir(path) || !includeTarget) {
            // Use `newDirectoryStream` to do the first-level traversal,
            // rather than relying on `walkFileTree`, because `walkFileTree`
            // does the unintuitive thing when the `path` being walked is a
            // symlink to a directory (it just returns the symlink path and
            // does not walking)
            val ds = Files.newDirectoryStream(pathNIO)
            val iter = ds.iterator()
            try {
              while (iter.hasNext && currentAction == Generator.Continue) Files.walkFileTree(
                iter.next(),
                opts,
                maxDepth - 1,
                new FileVisitor[java.nio.file.Path] {
                  def preVisitDirectory(dir: file.Path, attrs: BasicFileAttributes) = {
                    val dirP = Path(dir.toAbsolutePath)
                    val info = os.StatInfo.make(attrs)
                    if (skip(dirP, info)) FileVisitResult.SKIP_SUBTREE
                    else actionToResult {
                      if (preOrder && dirP != path) handleItem((dirP, info))
                      else {
                        attrsStack.append(info)
                        currentAction
                      }
                    }
                  }

                  def visitFile(file: java.nio.file.Path, attrs: BasicFileAttributes) = {
                    val fileP = Path(file.toAbsolutePath)
                    val info = os.StatInfo.make(attrs)
                    actionToResult(
                      if (skip(fileP, info)) currentAction
                      else handleItem((fileP, info))
                    )
                  }

                  def visitFileFailed(file: java.nio.file.Path, exc: IOException) =
                    actionToResult(currentAction)

                  def postVisitDirectory(dir: java.nio.file.Path, exc: IOException) = {
                    actionToResult(
                      if (preOrder) currentAction
                      else {
                        val dirP = Path(dir.toAbsolutePath)
                        if (dirP != path) {
                          handleItem((dirP, attrsStack.remove(attrsStack.length - 1)))
                        }
                        else currentAction
                      }
                    )
                  }
                }
              )
            } finally {
              ds.close()
            }
          }

          if (includeTarget && !preOrder) handleItem(
            (
              path,
              os.StatInfo.make(
                java.nio.file.Files.readAttributes(path.toNIO, classOf[BasicFileAttributes])
              )
            )
          )
          currentAction
        }
      }
    }
  }
}
