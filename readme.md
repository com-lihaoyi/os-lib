# OS-Lib

```scala
// Make sure working directory exists and is empty
val wd = os.pwd/"out"/"splash"
os.remove.all(wd)
os.makeDir.all(wd)

// Read/write files
os.write(wd/"file.txt", "hello")
os.read(wd/"file.txt") ==> "hello"

// Perform filesystem operations
os.copy(wd/"file.txt", wd/"copied.txt")
os.list(wd) ==> Seq(wd/"copied.txt", wd/"file.txt")

// Invoke subprocesses
val invoked = os.proc("cat", wd/"file.txt", wd/"copied.txt").call(cwd = wd)
invoked.out.trim ==> "hellohello"
```

OS-Lib is a simple Scala interface to common OS filesystem and subprocess APIs.
OS-Lib aims to make working with files and processes in Scala as simple as any
scripting language, while still providing the safety, flexibility and
performance you would expect from Scala.

OS-Lib aims to be a complete replacement for the
`java.nio.file.Files`/`java.nio.file.Paths`, `java.lang.ProcessBuilder`
`scala.io` and `scala.sys` APIs. You should not need to drop down to underlying
Java APIs, as OS-Lib exposes all relevant capabilities in an intuitive and
performant way. OS-Lib has no dependencies, and can be used in any Scala
codebase without worrying about jar or style conflicts.

- [Getting Started](#getting-started)
- [Cookbook](#cookbook)
    - [Concatenate text files](#concatenate-text-files)
    - [Recursive line count](#recursive-line-count)
    - [Largest three files](#largest-three-files)
    - [Moving files out of folder](#moving-files-out-of-folder)
    - [Calculate word frequencies](#calculate-word-frequencies)

- [Operations](#operations)

    Reading & Writing

    - [os.read](#osread)
    - [os.read.bytes](#osreadbytes)
    - [os.read.lines](#osreadlines)
    - [os.read.lines.stream](#osreadlinesstream)
    - [os.write](#oswrite)
    - [os.write.append](#oswriteappend)
    - [os.write.over](#oswriteover)

    Listing & Walking

    - [os.list](#oslist)
    - [os.list.stream](#osliststream)
    - [os.walk](#oswalk)
    - [os.walk.attrs](#oswalkattrs)
    - [os.walk.stream](#oswalkstream)
    - [os.walk.stream.attrs](#oswalkstreamattrs)

    Manipulating Files & Folders

    - [os.exists](#osexists)
    - [os.move](#osmove)
    - [os.move.into](#osmoveinto)
    - [os.move.over](#osmoveover)
    - [os.copy](#oscopy)
    - [os.copy.into](#oscopyinto)
    - [os.copy.over](#oscopyover)
    - [os.makeDir](#osmakedir)
    - [os.makeDir.all](#osmakedirall)
    - [os.remove](#osremove)
    - [os.remove.all](#osremoveall)
    - [os.hardlink](#oshardlink)
    - [os.symlink](#ossymlink)
    - [os.followLink](#osfollowlink)
    - [os.temp](#ostemp)
    - [os.temp.dir](#ostempdir)

    Filesystem Metadata

    - [os.stat](#osstat)
    - [os.stat.full](#osstatfull)
    - [os.isFile](#osisfile)
    - [os.isDir](#osisdir)
    - [os.isLink](#osislink)
    - [os.size](#ossize)
    - [os.mtime](#osmtime)

    Filesystem Permissions

    - [os.perms](#osperms)
    - [os.owner](#osowner)
    - [os.group](#osgroup)

    Spawning Subprocesses

    - [os.proc.call](#osproccall)
    - [os.proc.stream](#osprocstream)
    - [os.proc.spawn](#osprocspawn)

- [Data Types](#data-types)
    - [os.Path](#ospath)
        - [os.RelPath](#osrelpath)
        - [os.ResourcePath](#osresourcepath)
    - [os.Source](#ossource)
    - [os.Generator](#osgenerator)
    - [os.PermSet](#ospermset)

- [Changelog](#changelog)

## Getting Started

To begin using OS-Lib, first add it as a dependency to your project's build:

```scala
// SBT
"com.lihaoyi" %% "os-lib" % "0.1.2"
// Mill
ivy"com.lihaoyi::os-lib:0.1.2"
```

## Cookbook

Most operation in OS-Lib take place on [os.Path](#ospath)s, which are
constructed from a base path or working directory `wd`. Most often, the first
thing to do is to define a `wd` path representing the folder you want to work
with:

```scala
val wd = os.pwd/"my-test-folder"
```

You can of course multiple base paths, to use in different parts of your program
where convenient, or simply work with one of the pre-defined paths `os.pwd`,
`os.root`, or `os.home`.

### Concatenate text files
```scala
// Find and concatenate all .txt files directly in the working directory
os.write(
  wd/"all.txt",
  os.list(wd).filter(_.ext == "txt").map(os.read)
)

os.read(wd/"all.txt") ==>
  """I am cowI am cow
    |Hear me moo
    |I weigh twice as much as you
    |And I look good on the barbecue""".stripMargin
```

### Recursive line count
```scala
// Line-count of all .txt files recursively in wd
val lineCount = os.walk(wd)
  .filter(_.ext == "txt")
  .map(os.read.lines)
  .map(_.size)
  .sum

lineCount ==> 9
```

### Largest Three Files

```scala
// Find the largest three files in the given folder tree
val largestThree = os.walk(wd)
  .filter(os.isFile(_, followLinks = false))
  .map(x => os.size(x) -> x).sortBy(-_._1)
  .take(3)

largestThree ==> Seq(
  (711, wd / "misc" / "binary.png"),
  (81, wd / "Multi Line.txt"),
  (22, wd / "folder1" / "one.txt")
)
```

### Moving files out of folder
```scala
// Move all files inside the "misc" folder out of it
import os.{GlobSyntax, /}
os.list(wd/"misc").map(os.move.matching{case p/"misc"/x => p/x })
```

### Calculate word frequencies

```scala
// Calculate the word frequency of all the text files in the folder tree
def txt = os.walk(wd).filter(_.ext == "txt").map(os.read)
def freq(s: Seq[String]) = s groupBy (x => x) mapValues (_.length) toSeq
val map = freq(txt.flatMap(_.split("[^a-zA-Z0-9_]"))).sortBy(-_._2)
map
```

## Operations

### Reading & Writing

#### os.read

```scala
os.read(arg: os.ReadablePath): String
os.read(arg: os.ReadablePath, charSet: Codec): String
os.read(arg: os.Path,
        offset: Long = 0,
        count: Int = Int.MaxValue,
        charSet: Codec = java.nio.charset.StandardCharsets.UTF_8): String
```

Reads the contents of a [os.Path](#ospath) or other [os.Source](#ossource) as a
`java.lang.String`. Defaults to reading the entire file as UTF-8, but you can
also select a different `charSet` to use, and provide an `offset`/`count` to
read from if the source supports seeking.

```scala
os.read(wd / "File.txt") ==> "I am cow"
os.read(wd / "folder1" / "one.txt") ==> "Contents of folder one"
os.read(wd / "Multi Line.txt") ==>
  """I am cow
    |Hear me moo
    |I weigh twice as much as you
    |And I look good on the barbecue""".stripMargin
```
#### os.read.bytes

```scala
os.read.bytes(arg: os.ReadablePath): Array[Byte] 
os.read.bytes(arg: os.Path, offset: Long, count: Int): Array[Byte]
```

Reads the contents of a [os.Path](#ospath) or [os.Source](#ossource) as an
`Array[Byte]`; you can provide an `offset`/`count` to read from if the source
supports seeking.

```scala
os.read.bytes(wd / "File.txt") ==> "I am cow".getBytes
os.read.bytes(wd / "misc" / "binary.png").length ==> 711
```

#### os.read.lines

```scala
os.read.lines(arg: os.ReadablePath): IndexedSeq[String]
os.read.lines(arg: os.ReadablePath, charSet: Codec): IndexedSeq[String]
```

Reads the given [os.Path](#ospath) or other [os.Source](#ossource) as a string
and splits it into lines; defaults to reading as UTF-8, which you can override
by specifying a `charSet`.

```scala
os.read.lines(wd / "File.txt") ==> Seq("I am cow")
os.read.lines(wd / "Multi Line.txt") ==> Seq(
  "I am cow",
  "Hear me moo",
  "I weigh twice as much as you",
  "And I look good on the barbecue"
)
```

#### os.read.lines.stream

```scala
os.read.lines(arg: os.ReadablePath): os.Generator[String]
os.read.lines(arg: os.ReadablePath, charSet: Codec): os.Generator[String]
```

Identical to [os.read.lines](#osreadlines), but streams the results back to you
in a [os.Generator](#osgenerator) rather than accumulating them in memory.
Useful if the file is large.

```scala
os.read.lines.stream(wd / "File.txt").count() ==> 1
os.read.lines.stream(wd / "Multi Line.txt").count() ==> 4

// Streaming the lines to the console
for(line <- os.read.lines.stream(wd / "Multi Line.txt")){
  println(line)
}
```

#### os.write

```scala
os.write(target: Path, 
         data: os.Source, 
         perms: PermSet = null, 
         createFolders: Boolean = true): Unit
```

Writes data from the given file or [os.Source](#ossource) to a file at the
target [os.Path](#ospath). You can specify the filesystem permissions of the
newly created file by passing in a [os.PermSet](#ospermset).

This throws an exception if the file already exists. To over-write or append to
an existing file, see [os.write.over](#oswriteover) or
[os.write.append](#oswriteappend).

By default, this creates any necessary enclosing folders; you can disable this
behavior by setting `createFolders = false`

```scala
os.write(wd / "New File.txt", "New File Contents")
os.read(wd / "New File.txt") ==> "New File Contents"

os.write(wd / "NewBinary.bin", Array[Byte](0, 1, 2, 3))
os.read.bytes(wd / "NewBinary.bin") ==> Array[Byte](0, 1, 2, 3)
```

#### os.write.append

```scala
os.write.append(target: Path,
                data: os.Source,
                perms: PermSet = null,
                createFolders: Boolean = true): Unit
```

Similar to [os.write](#oswrite), except if the file already exists this appends
the written data to the existing file contents.

```scala
os.read(wd / "File.txt") ==> "I am cow"

os.write.append(wd / "File.txt", ", hear me moo")
os.read(wd / "File.txt") ==> "I am cow, hear me moo"

os.write.append(wd / "File.txt", ",\nI weigh twice as much as you")
os.read(wd / "File.txt") ==>
  "I am cow, hear me moo,\nI weigh twice as much as you"

os.read.bytes(wd / "misc" / "binary.png").length ==> 711
os.write.append(wd / "misc" / "binary.png", Array[Byte](1, 2, 3))
os.read.bytes(wd / "misc" / "binary.png").length ==> 714
```

#### os.write.over

```scala
os.write.over(target: Path,
              data: os.Source,
              perms: PermSet = null,
              offset: Long = 0,
              createFolders: Boolean = true,
              truncate: Boolean = true): Unit
```

Similar to [os.write](#oswrite), except if the file already exists this
over-writes the existing file contents. You can also pass in `truncate = false`
to avoid truncating the file if the new contents is shorter than the old
contents, and an `offset` to the file you want to write to.

```scala
os.read(wd / "File.txt") ==> "I am cow"
os.write.over(wd / "File.txt", "You are cow")

os.read(wd / "File.txt") ==> "You are cow"

os.write.over(wd / "File.txt", "We ", truncate = false)
os.read(wd / "File.txt") ==> "We  are cow"

os.write.over(wd / "File.txt", "s", offset = 8, truncate = false)
os.read(wd / "File.txt") ==> "We  are sow"
```

### Listing & Walking

#### os.list

```scala
os.list(p: Path): IndexedSeq[Path]
```

Returns all the files and folders directly within the given folder. If the given
path is not a folder, raises an error. Can be called via
[os.list.stream](#osliststream) to stream the results. To list files recursively,
use [os.walk](#oswalk).

```scala
os.list(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")
os.list(wd / "folder2") ==> Seq(
  wd / "folder2" / "nestedA",
  wd / "folder2" / "nestedB"
)
```

#### os.list.stream

```scala
os.list.stream(p: Path): os.Generator[Path]
```

Similar to [os.list](#oslist), except provides a [os.Generator](#osgenerator) of
results rather than accumulating all of them in memory. Useful if the result set
is large.

```scala
os.list.stream(wd / "folder2").count() ==> 2

// Streaming the listed files to the console
for(line <- os.list.stream(wd / "folder2")){
  println(line)
}
```

#### os.walk

```scala
os.walk(path: Path,
        skip: Path => Boolean = _ => false,
        preOrder: Boolean = true,
        followLinks: Boolean = false,
        maxDepth: Int = Int.MaxValue): IndexedSeq[Path]
```

Recursively walks the given folder and returns the paths of every file or folder
within.

You can pass in a `skip` callback to skip files or folders you are not
interested in. This can avoid walking entire parts of the folder hierarchy,
saving time as compared to filtering them after the fact.

By default, the paths are returned as a pre-order traversal: the enclosing
folder is occurs first before any of it's contents. You can pass in `preOrder =
false` to turn it into a post-order traversal, such that the enclosing folder
occurs last after all it's contents.

`os.walk` returns but does not follow symlinks; pass in `followLinks = true` to
override that behavior. You can also specify a maximum depth you wish to walk
via the `maxDepth` parameter.

```scala
os.walk(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")

os.walk(wd / "folder2") ==> Seq(
  wd / "folder2" / "nestedA",
  wd / "folder2" / "nestedA" / "a.txt",
  wd / "folder2" / "nestedB",
  wd / "folder2" / "nestedB" / "b.txt"
)

os.walk(wd / "folder2", preOrder = false) ==> Seq(
  wd / "folder2" / "nestedA" / "a.txt",
  wd / "folder2" / "nestedA",
  wd / "folder2" / "nestedB" / "b.txt",
  wd / "folder2" / "nestedB"
)

os.walk(wd / "folder2", maxDepth = 1) ==> Seq(
  wd / "folder2" / "nestedA",
  wd / "folder2" / "nestedB"
)

os.walk(wd / "folder2", skip = _.last == "nestedA") ==> Seq(
  wd / "folder2" / "nestedB",
  wd / "folder2" / "nestedB" / "b.txt"
)
```

#### os.walk.attrs

```scala
os.walk.attrs(path: Path,
              skip: Path => Boolean = _ => false,
              preOrder: Boolean = true,
              followLinks: Boolean = false,
              maxDepth: Int = Int.MaxValue): IndexedSeq[(Path, BasicFileAttributes)]
```

Similar to [os.walk](#oswalk), except it also provides the filesystem metadata
of every path that it returns. Can save time by allowing you to avoid querying
the filesystem for metadata later.

```scala
val filesSortedBySize = os.walk.attrs(wd / "misc", followLinks = true)
  .sortBy{case (p, attrs) => attrs.size()}
  .collect{case (p, attrs) if attrs.isRegularFile => p}

filesSortedBySize ==> Seq(
  wd / "misc" / "echo",
  wd / "misc" / "file-symlink",
  wd / "misc" / "echo_with_wd",
  wd / "misc" / "folder-symlink" / "one.txt",
  wd / "misc" / "binary.png"
)
```

#### os.walk.stream

```scala
os.walk.stream(path: Path,
            skip: Path => Boolean = _ => false,
            preOrder: Boolean = true,
            followLinks: Boolean = false,
            maxDepth: Int = Int.MaxValue): os.Generator[Path]
```


Similar to [os.walk](#oswalk), except returns a [os.Generator](#osgenerator) of
the results rather than accumulating them in memory. Useful if you are walking
very large folder hierarchies, or if you wish to begin processing the output
even before the walk has completed.

```scala
os.walk.stream(wd / "folder1").count() ==> 1

os.walk.stream(wd / "folder2").count() ==> 4

os.walk.stream(wd / "folder2", skip = _.last == "nestedA").count() ==> 2
```

#### os.walk.stream.attrs

```scala
os.walk.stream.attrs(path: Path,
                   skip: Path => Boolean = _ => false,
                   preOrder: Boolean = true,
                   followLinks: Boolean = false,
                   maxDepth: Int = Int.MaxValue): os.Generator[Path]
```

Similar to [os.walk.stream](#oswalkstream), except it also provides the filesystem
metadata of every path that it returns. Can save time by allowing you to avoid
querying the filesystem for metadata later.

```scala
def totalFileSizes(p: os.Path) = os.walk.stream.attrs(p)
  .collect{case (p, attrs) if attrs.isRegularFile => attrs.size()}
  .sum

totalFileSizes(wd / "folder1") ==> 22
totalFileSizes(wd / "folder2") ==> 40
```
### Manipulating Files & Folders

#### os.exists

```scala
os.exists(p: Path, followLinks: Boolean = true): Boolean
```

Checks if a file or folder exists at the specified path

```scala
os.exists(wd / "File.txt") ==> true
os.exists(wd / "folder1") ==> true
os.exists(wd / "doesnt-exist") ==> false

os.exists(wd / "misc" / "file-symlink") ==> true
os.exists(wd / "misc" / "folder-symlink") ==> true
os.exists(wd / "misc" / "broken-symlink") ==> false
os.exists(wd / "misc" / "broken-symlink", followLinks = false) ==> true
```

#### os.move

```scala
os.move(from: Path, to: Path): Unit
os.move(from: Path, to: Path, createFolders: Boolean): Unit
```

Moves a file or folder from one path to another. Errors out if the destination
path already exists, or is within the source path.

```scala
os.list(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")
os.move(wd / "folder1" / "one.txt", wd / "folder1" / "first.txt")
os.list(wd / "folder1") ==> Seq(wd / "folder1" / "first.txt")

os.list(wd / "folder2") ==> Seq(wd / "folder2" / "nestedA", wd / "folder2" / "nestedB")
os.move(wd / "folder2" / "nestedA", wd / "folder2" / "nestedC")
os.list(wd / "folder2") ==> Seq(wd / "folder2" / "nestedB", wd / "folder2" / "nestedC")

os.read(wd / "File.txt") ==> "I am cow"
os.move(wd / "Multi Line.txt", wd / "File.txt", replaceExisting = true)
os.read(wd / "File.txt") ==>
  """I am cow
    |Hear me moo
    |I weigh twice as much as you
    |And I look good on the barbecue""".stripMargin
```

#### os.move.matching

```scala
os.move.matching(t: PartialFunction[Path, Path]): PartialFunction[Path, Unit]
```

`os.move` can also be used as a transformer, via `os.move.matching`. This lets
you use `.map` or `.collect` on a list of paths, and move all of them at once,
e.g. to rename all `.txt` files within a folder tree to `.data`:

```scala
import os.{GlobSyntax, /}
os.walk(wd / "folder2") ==> Seq(
  wd / "folder2" / "nestedA",
  wd / "folder2" / "nestedA" / "a.txt",
  wd / "folder2" / "nestedB",
  wd / "folder2" / "nestedB" / "b.txt"
)

os.walk(wd/'folder2).collect(os.move.matching{case p/g"$x.txt" => p/g"$x.data"})

os.walk(wd / "folder2") ==> Seq(
  wd / "folder2" / "nestedA",
  wd / "folder2" / "nestedA" / "a.data",
  wd / "folder2" / "nestedB",
  wd / "folder2" / "nestedB" / "b.data"
)
```

#### os.move.into

```scala
os.move.into(from: Path, to: Path): Unit
```

Move the given file or folder *into* the destination folder

```scala
os.list(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")
os.move.into(wd / "File.txt", wd / "folder1")
os.list(wd / "folder1") ==> Seq(wd / "folder1" / "File.txt", wd / "folder1" / "one.txt")
```

#### os.move.over

```scala
os.move.over(from: Path, to: Path): Unit
```

Move a file or folder from one path to another, and *overwrite* any file or
folder than may already be present at that path

```scala
os.list(wd / "folder2") ==> Seq(wd / "folder2" / "nestedA", wd / "folder2" / "nestedB")
os.move.over(wd / "folder1", wd / "folder2")
os.list(wd / "folder2") ==> Seq(wd / "folder2" / "one.txt")
```

#### os.copy

```scala
os.copy(from: Path, to: Path): Unit
os.copy(from: Path, to: Path, createFolders: Boolean): Unit
```

Copy a file or folder from one path to another. Recursively copies folders with
all their contents. Errors out if the destination path already exists, or is
within the source path.

```scala
os.list(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")
os.copy(wd / "folder1" / "one.txt", wd / "folder1" / "first.txt")
os.list(wd / "folder1") ==> Seq(wd / "folder1" / "first.txt", wd / "folder1" / "one.txt")

os.list(wd / "folder2") ==> Seq(wd / "folder2" / "nestedA", wd / "folder2" / "nestedB")
os.copy(wd / "folder2" / "nestedA", wd / "folder2" / "nestedC")
os.list(wd / "folder2") ==> Seq(
  wd / "folder2" / "nestedA",
  wd / "folder2" / "nestedB",
  wd / "folder2" / "nestedC"
)

os.read(wd / "File.txt") ==> "I am cow"
os.copy(wd / "Multi Line.txt", wd / "File.txt", replaceExisting = true)
os.read(wd / "File.txt") ==>
  """I am cow
    |Hear me moo
    |I weigh twice as much as you
    |And I look good on the barbecue""".stripMargin
    ```
    
`os.copy` can also be used as a transformer:

```scala
os.copy.matching(t: PartialFunction[Path, Path]): PartialFunction[Path, Unit]
```

This lets you use `.map` or `.collect` on a list of paths, and copy all of them
at once:

```scala
paths.map(os.copy.matching{case p/"scala"/file => p/"java"/file})
```

#### os.copy.into

```scala
os.copy.into(from: Path, to: Path): Unit
```

Copy the given file or folder *into* the destination folder

```scala
os.list(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")
os.copy.into(wd / "File.txt", wd / "folder1")
os.list(wd / "folder1") ==> Seq(wd / "folder1" / "File.txt", wd / "folder1" / "one.txt")
```

#### os.copy.over

```scala
os.copy.over(from: Path, to: Path): Unit
```

Similar to [os.copy](#oscopy), but if the destination file already exists then
overwrite it instead of erroring out.

```scala
os.list(wd / "folder2") ==> Seq(wd / "folder2" / "nestedA", wd / "folder2" / "nestedB")
os.copy.over(wd / "folder1", wd / "folder2")
os.list(wd / "folder2") ==> Seq(wd / "folder2" / "one.txt")
```
#### os.makeDir

```scala
os.makeDir(path: Path): Unit
os.makeDir(path: Path, perms: PermSet): Unit
```

Create a single directory at the specified path. Optionally takes in a
[os.PermSet](#ospermset) to specify the filesystem permissions of the created
directory.

Errors out if the directory already exists, or if the parent directory of the
specified path does not exist. To automatically create enclosing directories and
ignore the destination if it already exists, using
[os.makeDir.all](#osmakedirall)

```scala
os.exists(wd / "new_folder") ==> false
os.makeDir(wd / "new_folder")
os.exists(wd / "new_folder") ==> true
```

#### os.makeDir.all

```scala
os.makeDir.all(path: Path): Unit
os.makeDir.all(path: Path, perms: PermSet): Unit
```

Similar to [os.makeDir](#osmakedir), but automatically creates any necessary
enclosing directories if they do not exist, and does not raise an error if the
destination path already containts a directory.

```scala
os.exists(wd / "new_folder") ==> false
os.makeDir.all(wd / "new_folder" / "inner" / "deep")
os.exists(wd / "new_folder" / "inner" / "deep") ==> true
```
#### os.remove

```scala
os.remove(target: Path): Unit
```

Remove the target file or folder. Folders need to be empty to be removed; if you
want to remove a folder tree recursively, use [os.remove.all](#osremoveall)

```scala
os.exists(wd / "File.txt") ==> true
os.remove(wd / "File.txt")
os.exists(wd / "File.txt") ==> false

os.exists(wd / "folder1" / "one.txt") ==> true
os.remove(wd / "folder1" / "one.txt")
os.remove(wd / "folder1")
os.exists(wd / "folder1" / "one.txt") ==> false
os.exists(wd / "folder1") ==> false
```
#### os.remove.all

```scala
os.remove.all(target: Path): Unit
```

Remove the target file or folder; if it is a folder and not empty, recursively
removing all it's contents before deleting it.

```scala
os.exists(wd / "folder1" / "one.txt") ==> true
os.remove.all(wd / "folder1")
os.exists(wd / "folder1" / "one.txt") ==> false
os.exists(wd / "folder1") ==> false
```

#### os.hardlink

```scala
os.hardlink(src: Path, dest: Path, perms): Unit
```

Create a hardlink to the source path from the destination path

```scala
os.hardlink(wd / "File.txt", wd / "Linked.txt")
os.exists(wd / "Linked.txt")
os.read(wd / "Linked.txt") ==> "I am cow"
os.isLink(wd / "Linked.txt") ==> false
```

#### os.symlink

```scala
os.symlink(src: Path, dest: Path, perms: PermSet = null): Unit
```

Create a symbolic to the source path from the destination path. Optionally takes
a [os.PermSet](#ospermset) to customize the filesystem permissions of the symbolic
link.
```scala
os.symlink(wd / "File.txt", wd / "Linked.txt")
os.exists(wd / "Linked.txt")
os.read(wd / "Linked.txt") ==> "I am cow"
os.isLink(wd / "Linked.txt") ==> true
```

#### os.followLink

```scala
os.followLink(src: Path): Option[Path]
```

Attempts to any symbolic links in the given path and return the canonical path.
Returns `None` if the path cannot be resolved (i.e. some symbolic link in the
given path is broken)

```scala
os.followLink(wd / "misc" / "file-symlink") ==> Some(wd / "File.txt")
os.followLink(wd / "misc" / "folder-symlink") ==> Some(wd / "folder1")
os.followLink(wd / "misc" / "broken-symlink") ==> None
```

#### os.temp

```scala
os.temp(contents: os.Source = null,
        dir: Path = null,
        prefix: String = null,
        suffix: String = null,
        deleteOnExit: Boolean = true,
        perms: PermSet = null): Path
```

Creates a temporary file. You can optionally provide a `dir` to specify where
this file lives, file-`prefix` and file-`suffix` to customize what it looks
like, and a [os.PermSet](#ospermset) to customize its filesystem permissions.

Passing in a [os.Source](#ossource) will initialize the contents of that file to
the provided data; otherwise it is created empty.

By default, temporary files are deleted on JVM exit. You can disable that
behavior by setting `deleteOnExit = false`

```scala
val tempOne = os.temp("default content")
os.read(tempOne) ==> "default content"
os.write.over(tempOne, "Hello")
os.read(tempOne) ==> "Hello"
```

#### os.temp.dir

```scala
os.temp.dir(dir: Path = null,
            prefix: String = null,
            deleteOnExit: Boolean = true,
            perms: PermSet = null): Path
```


Creates a temporary directory. You can optionally provide a `dir` to specify
where this file lives, a `prefix` to customize what it looks like, and a
[os.PermSet](#ospermset) to customize its filesystem permissions.

By default, temporary directories are deleted on JVM exit. You can disable that
behavior by setting `deleteOnExit = false`

```scala
val tempDir = os.temp.dir()
os.list(tempDir) ==> Nil
os.write(tempDir / "file", "Hello")
os.list(tempDir) ==> Seq(tempDir / "file")
```

### Filesystem Metadata

#### os.stat

```scala
os.stat(p: os.Path, followLinks: Boolean = true): os.StatInfo
```

Reads in the basic filesystem metadata for the given file. By default follows
symbolic links to read the metadata of whatever the link is pointing at; set
`followLinks = false` to disable that and instead read the metadata of the
symbolic link itself.

```scala
os.stat(wd / "File.txt").size ==> 8
os.stat(wd / "Multi Line.txt").size ==> 81
os.stat(wd / "folder1").fileType ==> os.FileType.Dir
```
#### os.stat.full

```scala
os.stat.full(p: os.Path, followLinks: Boolean = true): os.FullStatInfo
```

Reads in the full filesystem metadata for the given file. By default follows
symbolic links to read the metadata of whatever the link is pointing at; set
`followLinks = false` to disable that and instead read the metadata of the
symbolic link itself.

```
os.stat.full(wd / "File.txt").size ==> 8
os.stat.full(wd / "Multi Line.txt").size ==> 81
os.stat.full(wd / "folder1").fileType ==> os.FileType.Dir
```
#### os.isFile

```scala
os.isFile(p: Path, followLinks: Boolean = true): Boolean
```

Returns `true` if the given path is a file. Follows symbolic links by default,
pass in `followLinks = false` to not do so.

```scala
os.isFile(wd / "File.txt") ==> true
os.isFile(wd / "folder1") ==> false

os.isFile(wd / "misc" / "file-symlink") ==> true
os.isFile(wd / "misc" / "folder-symlink") ==> false
os.isFile(wd / "misc" / "file-symlink", followLinks = false) ==> false
```

#### os.isDir

```scala
os.isDir(p: Path, followLinks: Boolean = true): Boolean
```

Returns `true` if the given path is a folder. Follows symbolic links by default,
pass in `followLinks = false` to not do so.

```scala
os.isDir(wd / "File.txt") ==> false
os.isDir(wd / "folder1") ==> true

os.isDir(wd / "misc" / "file-symlink") ==> false
os.isDir(wd / "misc" / "folder-symlink") ==> true
os.isDir(wd / "misc" / "folder-symlink", followLinks = false) ==> false
```
#### os.isLink


```scala
os.isLink(p: Path, followLinks: Boolean = true): Boolean
```

Returns `true` if the given path is a symbolic link. Follows symbolic links by
default, pass in `followLinks = false` to not do so.

```scala
os.isLink(wd / "misc" / "file-symlink") ==> true
os.isLink(wd / "misc" / "folder-symlink") ==> true
os.isLink(wd / "folder1") ==> false
```
#### os.size

```scala
os.size(p: Path): Long
```

Returns the size of the given file, in bytes

```scala
os.size(wd / "File.txt") ==> 8
os.size(wd / "Multi Line.txt") ==> 81
```
#### os.mtime

```scala
os.mtime(p: Path): Long
os.mtime.set(p: Path, millis: Long): Unit
```

Gets or sets the last-modified timestamp of the given file, in milliseconds

```scala
os.mtime.set(wd / "File.txt", 0)
os.mtime(wd / "File.txt") ==> 0

os.mtime.set(wd / "File.txt", 90000)
os.mtime(wd / "File.txt") ==> 90000
os.mtime(wd / "misc" / "file-symlink") ==> 90000

os.mtime.set(wd / "misc" / "file-symlink", 70000)
os.mtime(wd / "File.txt") ==> 70000
os.mtime(wd / "misc" / "file-symlink") ==> 70000
assert(os.mtime(wd / "misc" / "file-symlink", followLinks = false) != 40000)
```
### Filesystem Permissions

#### os.perms

```scala
os.perms(p: Path, followLinks: Boolean = true): PermSet
os.perms.set(p: Path, arg2: PermSet): Unit
```

Gets or sets the filesystem permissions of the given file or folder, as an
[os.PermSet](#ospermset).

Note that if you want to create a file or folder with a given set of
permissions, you can pass in an [os.PermSet](#ospermset) to [os.write](#oswrite)
or [os.makeDir](#osmakedir). That will ensure the file or folder is created
atomically with the given permissions, rather than being created with the
default set of permissions and having `os.perms.set` over-write them later

```scala
os.perms.set(wd / "File.txt", "rwxrwxrwx")
os.perms(wd / "File.txt").toString() ==> "rwxrwxrwx"
os.perms(wd / "File.txt").toInt() ==> Integer.parseInt("777", 8)

os.perms.set(wd / "File.txt", Integer.parseInt("755", 8))
os.perms(wd / "File.txt").toString() ==> "rwxr-xr-x"

os.perms.set(wd / "File.txt", "r-xr-xr-x")
os.perms.set(wd / "File.txt", Integer.parseInt("555", 8))
```

#### os.owner

```scala
os.owner(p: Path, followLinks: Boolean = true): UserPrincipal
os.owner.set(arg1: Path, arg2: UserPrincipal): Unit
os.owner.set(arg1: Path, arg2: String): Unit
```

Gets or sets the owner of the given file or folder. Note that your process needs
to be running as the `root` user in order to do this.

```scala
val originalOwner = os.owner(wd / "File.txt")

os.owner.set(wd / "File.txt", "nobody")
os.owner(wd / "File.txt").getName ==> "nobody"

os.owner.set(wd / "File.txt", originalOwner)
```


#### os.group

```scala
os.group(p: Path, followLinks: Boolean = true): GroupPrincipal
os.group.set(arg1: Path, arg2: GroupPrincipal): Unit
os.group.set(arg1: Path, arg2: String): Unit
```

Gets or sets the owning group of the given file or folder. Note that your
process needs to be running as the `root` user in order to do this.

```scala
val originalOwner = os.owner(wd / "File.txt")

os.owner.set(wd / "File.txt", "nobody")
os.owner(wd / "File.txt").getName ==> "nobody"

os.owner.set(wd / "File.txt", originalOwner)
```

### Spawning Subprocesses

Subprocess are spawned using `os.proc(command: os.Shellable*).foo(...)` calls,
where the `command: Shellable*` sets up the basic command you wish to run and
`.foo(...)` specifies how you want to run it. `os.Shellable` represents a value
that can make up part of your subprocess command, and the following values can
be used as `os.Shellable`s:

- `java.lang.String`
- `scala.Symbol`
- `os.Path`
- `os.RelPath`
- `T: Numeric`
- `Iterable[T]`s of any of the above

Most of the subprocess commands also let you redirect the subprocess's
input/output streams via `os.Redirect` values: whether to inherit them from the
parent process, stream them into buffers, or output them to files. The following
`os.Redirect` values are most common:

- `os.Pipe`: the default, this connects the subprocess's stream to the parent
  process via pipes; if used on its stdin this lets the parent process write to
  the subprocess via `java.lang.Process#getOutputStream`, and if used on its
  stdout it lets the parent process read from the subprocess via
  `java.lang.Process#getInputStream` and `java.lang.Process#getErrorStream`.

- `os.Inherit`: inherits the stream from the parent process. This lets the
  subprocess read directly from the paren process's standard input or write
  directly to the parent process's standard output or error

- `os.RedirectToPath`: connects the subprocess's stream to the given filesystem
  path, reading it's standard input from a file or writing it's standard
  output/error to the file.

Often, if you are only interested in capturing the standard output of the
subprocess but want any errors sent to the console, you might set `stderr =
os.Inherit` while leaving `stdout = os.Pipe`.

#### os.proc.call

```scala
os.proc(command: os.Shellable*)
  .call(cwd: Path = null,
        env: Map[String, String] = null,
        stdin: os.Source = Array[Byte](),
        stdout: os.Redirect = os.Pipe,
        stderr: os.Redirect = os.Pipe,
        mergeErrIntoOut: Boolean = false,
        timeout: Long = Long.MaxValue,
        check: Boolean = false,
        propagateEnv: Boolean = true): CommandResult
```

Invokes the given subprocess like a function, passing in input and returning a
`CommandResult`. You can then call `result.exitCode` to see how it exited, or
`result.out.bytes` or `result.err.string` to access the aggregated stdout and
stderr of the subprocess in a number of convenient ways.

`call` provides a number of parameters that let you configure how the subprocess
is run:

- `cwd`: the working directory of the subprocess
- `env`: any additional environment variables you wish to set in the subprocess
- `stdin`: any data you wish to pass to the subprocess's standard input
- `stdout`/`stderr`: these are `os.Redirect`s that let you configure how the
  processes output/error streams are configured.
- `mergeErrIntoOut`: merges the subprocess's stderr stream into it's stdout
- `timeout`: how long to wait for the subprocess to complete
- `check`: enable this to throw an exception if the subprocess fails with a
  non-zero exit code
- `propagateEnv`: disable this to avoid passing in this parent process's
  environment variables to the subprocess


```scala
val res = os.proc('ls, wd/"folder2").call()

res.exitCode ==> 0

res.out.string ==>
  """nestedA
    |nestedB
    |""".stripMargin

res.out.trim ==>
  """nestedA
    |nestedB""".stripMargin

res.out.lines ==> Seq(
  "nestedA",
  "nestedB"
)

res.out.bytes


val fail = os.proc('ls, "doesnt-exist").call(cwd = wd)

fail.exitCode ==> 1

fail.out.string ==> ""

assert(fail.err.string.contains("No such file or directory"))
```

If you want to spawn an interactive subprocess, such as `vim`, `less`, or a
`python` shell, set all of `stdin`/`stdout`/`stderr` to `os.Inherit`:

```scala
os.proc("vim").call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
```

#### os.proc.stream

```scala

os.proc(command: os.Shellable*)
  .stream(cwd: Path = null,
          env: Map[String, String] = null,
          stdin: os.Source = Array[Byte](),
          onOut: (Array[Byte], Int) => Unit,
          onErr: (Array[Byte], Int) => Unit,
          stdout: os.Redirect = os.Pipe,
          stderr: os.Redirect = os.Pipe,
          mergeErrIntoOut: Boolean = false,
          timeout: Long = Long.MaxValue,
          propagateEnv: Boolean = true): Int
```

Similar to [os.proc.call](#osproccall), but instead of aggregating the process's
standard output/error streams for you, you pass in `onOut`/`onErr` callbacks to
receive the data as it is generated.

Returns the exit code of the subprocess once it terminates

```scala
var lineCount = 1
os.proc('find, ".").stream(
  cwd = wd,
  onOut = (buf, len) => lineCount += buf.slice(0, len).count(_ == '\n'),
  onErr = (buf, len) => () // do nothing
)
lineCount ==> 21
```
#### os.proc.spawn

```scala
os.proc(command: os.Shellable*)
  .spawn(cwd: Path = null,
         env: Map[String, String] = null,
         stdin: os.Redirect = os.Pipe,
         stdout: os.Redirect = os.Pipe,
         stderr: os.Redirect = os.Pipe,
         mergeErrIntoOut: Boolean = false,
         propagateEnv: Boolean = true): java.lang.Process
```

The most flexible of the `os.proc` calls, `os.proc.spawn` simply configures and
starts a subprocess, and returns it as a `java.lang.Process` for you to interact
with however you like, e.g. by sending commands to it's `stdin` and reading from
it's `stdout`.

```scala
val sub = os.proc("python", "-c", "print eval(raw_input())").spawn(cwd = wd)
val out = new BufferedReader(new InputStreamReader(sub.getInputStream))
sub.getOutputStream.write("1 + 2 + 3\n".getBytes)
sub.getOutputStream.flush()
out.readLine() ==> "6"
```

## Data Types

### os.Path

OS-Lib uses strongly-typed data-structures to represent filesystem paths. The
two basic versions are:

- [os.Path](#ospath): an absolute path, starting from the root
- [os.RelPath](#osrelpath): a relative path, not rooted anywhere


Generally, almost all commands take absolute `os.Path`s. These are basically
`java.nio.file.Path`s with additional guarantees:

- `os.Path`s are always absolute. Relative paths are a separate type
  [os.RelPath](#osrelpath)

- `os.Path`s are always canonical. You will never find `.` or `..` segments in
  them, and never need to worry about calling `.normalize` before operations.

Absolute paths can be created in a few ways:

```scala
// Get the process' Current Working Directory. As a convention
// the directory that "this" code cares about (which may differ
// from the pwd) is called `wd`
val wd = os.pwd

// A path nested inside `wd`
wd/'folder/'file

// A path starting from the root
os.root/'folder/'file

// A path with spaces or other special characters
wd/"My Folder"/"My File.txt"

// Up one level from the wd
wd/os.up

// Up two levels from the wd
wd/os.up/os.up
```

Note that there are no in-built operations to change the `os.pwd`. In general
you should not need to: simply defining a new path, e.g.

```scala
val target = os.pwd/'target
```

Should be sufficient for most needs.

Above, we made use of the `os.pwd` built-in path. There are a number of Paths
built into Ammonite:

- `os.pwd`: The current working directory of the process. This can't be changed
  in Java, so if you need another path to work with the convention is to define
  a `os.wd` variable.
- `os.root`: The root of the filesystem.
- `os.home`: The home directory of the current user.
- `os.temp()`/`os.temp.dir()`: Creates a temporary file/folder and returns the
  path.

#### os.RelPath

`os.RelPath`s represent relative paths. These are basically defined as:

```scala
class RelPath private[ops] (segments0: Array[String], val ups: Int)
```

The same data structure as Paths, except that they can represent a number of ups
before the relative path is applied. They can be created in the following ways:

```scala
// The path "folder/file"
val rel1 = os.rel/'folder/'file
val rel2 = os.rel/"folder"/"file"

// The path "file"
val rel3 = os.rel/'file

// The relative difference between two paths
val target = os.pwd/'target/'file
assert((target.relativeTo(os.pwd)) == os.rel/'target/'file)

// `up`s get resolved automatically
val minus = os.pwd.relativeTo(target)
val ups = os.up/os.up
assert(minus == ups)
```

In general, very few APIs take relative paths. Their main purpose is to be
combined with absolute paths in order to create new absolute paths. e.g.

```scala
val target = os.pwd/'target/'file
val difference = target.relativeTo(os.pwd)
val newBase = os.root/'code/'server
assert(newBase/difference == os.root/'code/'server/'target/'file)
```

`os.up` is a relative path that comes in-built:

```scala
val target = os.root/'target/'file
assert(target/os.up == os.root/'target)
```

Note that all paths, both relative and absolute, are always expressed in a
canonical manner:

```scala
assert((os.root/'folder/'file/up).toString == "/folder")
// not "/folder/file/.."

assert(('folder/'file/up).toString == "folder")
// not "folder/file/.."
```

So you don't need to worry about canonicalizing your paths before comparing them
for equality or otherwise manipulating them.

#### Path Operations

OS-Lib's paths are transparent data-structures, and you can always access the
segments and ups directly. Nevertheless, Ammonite defines a number of useful
operations that handle the common cases of dealing with these paths:

In this definition, ThisType represents the same type as the current path; e.g.
a Path's / returns a Path while a RelPath's / returns a RelPath. Similarly, you
can only compare or subtract paths of the same type.

Apart from [os.RelPath](#osrelpath)s themselves, a number of other data
structures are convertible into [os.RelPath](#osrelpath)s when spliced into a
path using `/`:

- `String`s
- `Symbol`s
- `Array[T]`s where `T` is convertible into a RelPath
- `Seq[T]`s where `T` is convertible into a RelPath

#### Constructing Paths

Apart from built-ins like `os.pwd` or `os.root` or `os.home`, you can also
construct Paths from `String`s, `java.io.File`s or `java.nio.file.Path`s:

```scala
val relStr = "hello/cow/world/.." val absStr = "/hello/world"

assert(
  RelPath(relStr) == 'hello/'cow,
  // Path(...) also allows paths starting with ~,
  // which is expanded to become your home directory
  Path(absStr) == root/'hello/'world
)

// You can also pass in java.io.File and java.nio.file.Path
// objects instead of Strings when constructing paths
val relIoFile = new java.io.File(relStr)
val absNioFile = java.nio.file.Paths.get(absStr)

assert(
  RelPath(relIoFile) == 'hello/'cow,
  Path(absNioFile) == root/'hello/'world,
  Path(relIoFile, root/'base) == root/'base/'hello/'cow
)
```
Trying to construct invalid paths fails with exceptions:
```scala
val relStr = "hello/.."
intercept[java.lang.IllegalArgumentException]{
  Path(relStr)
}

val absStr = "/hello"
intercept[java.lang.IllegalArgumentException]{
  RelPath(absStr)
}

val tooManyUpsStr = "/hello/../.."
intercept[PathError.AbsolutePathOutsideRoot.type]{
  Path(tooManyUpsStr)
}
```

As you can see, attempting to parse a relative path with [os.Path](#ospath) or
an absolute path with [os.RelPath](#osrelpath) throws an exception. If you're
uncertain about what kind of path you are getting, you could use `BasePath` to
parse it :

```scala
val relStr = "hello/cow/world/.."
val absStr = "/hello/world"
assert(
  FilePath(relStr) == 'hello/'cow,
  FilePath(absStr) == root/'hello/'world
)
```

This converts it into a `BasePath`, which is either a [os.Path](#ospath) or
[os.RelPath](#osrelpath). It's then up to you to pattern-match on the types and
decide what you want to do in each case.

You can also pass in a second argument to `Path(..., base)`. If the path being
parsed is a relative path, this base will be used to coerce it into an absolute
path:

```scala
val relStr = "hello/cow/world/.."
val absStr = "/hello/world"
val basePath: FilePath = FilePath(relStr)
assert(
  os.Path(relStr, os.root/'base) == os.root/'base/'hello/'cow,
  os.Path(absStr, os.root/'base) == os.root/'hello/'world,
  os.Path(basePath, os.root/'base) == os.root/'base/'hello/'cow,
  os.Path(".", os.pwd).last != ""
)
```

For example, if you wanted the common behavior of converting relative paths to
absolute based on your current working directory, you can pass in `os.pwd` as
the second argument to `Path(...)`. Apart from passing in Strings or
java.io.Files or java.nio.file.Paths, you can also pass in BasePaths you parsed
early as a convenient way of converting it to a absolute path, if it isn't
already one.

In general, OS-Lib is very picky about the distinction between relative and
absolute paths, and doesn't allow "automatic" conversion between them based on
current-working-directory the same way many other filesystem APIs (Bash, Java,
Python, ...) do. Even in cases where it's uncertain, e.g. you're taking user
input as a String, you have to either handle both possibilities with BasePath or
explicitly choose to convert relative paths to absolute using some base.

#### os.ResourcePath

In addition to manipulating paths on the filesystem, you can also manipulate
`os.ResourcePath` in order to read resources off of the Java classpath. By
default, the path used to load resources is absolute, using the
`Thread.currentThread().getContextClassLoader`.

```scala
val contents = os.read(os.resource/'test/'ammonite/'ops/'folder/"file.txt")
assert(contents.contains("file contents lols"))
```

You can also pass in a classloader explicitly to the resource call:

```scala
val cl = getClass.getClassLoader
val contents2 = os.read(os.resource(cl)/'test/'ammonite/'ops/'folder/"file.txt")
assert(contents2.contains("file contents lols"))
```

If you want to load resources relative to a particular class, pass in a class
for the resource to be relative, or getClass to get something relative to the
current class.

```scala
val cls = classOf[test.os.Testing]
val contents = os.read(os.resource(cls)/'folder/"file.txt")
assert(contents.contains("file contents lols"))

val contents2 = os.read(os.resource(getClass)/'folder/"file.txt")
assert(contents2.contains("file contents lols"))
```

In both cases, reading resources is performed as if you did not pass a leading
slash into the `getResource("foo/bar")` call. In the case of
`ClassLoader#getResource`, passing in a leading slash is never valid, and in the
case of `Class#getResource`, passing in a leading slash is equivalent to calling
`getResource` on the ClassLoader.

OS-Lib ensures you only use the two valid cases in the API, without a leading
slash, and not the two cases with a leading slash which are redundant (in the
case of `Class#getResource`, which can be replaced by `ClassLoader#getResource`)
or invalid (a leading slash with `ClassLoader#getResource`)

Note that you can only use `os.read` from resource paths; you can't write to them or
perform any other filesystem operations on them, since they're not really files.

Note also that resources belong to classloaders, and you may have multiple
classloaders in your application e.g. if you are running in a servlet or REPL.
Make sure you use the correct classloader (or a class belonging to the correct
classloader) to load the resources you want, or else it might not find them.

### os.Source

Many operations in OS-Lib operate on `os.Source`s. These represent values that
can provide data which you can then use to write, transmit, etc.

By default, the following types of values can be used where-ever `os.Source`s
are required:

- `os.Path`
- `os.ResourcePath`
- `Array[Byte]`
- `java.lang.String` (these are treated as UTF-8)
- `java.io.InputStream`
- `java.nio.channels.SeekableByteChannel`
- Any `TraversableOnce[T]` of the above: e.g. `Seq[String]`,
  `List[Array[Byte]]`, etc.

Some operations only work on `os.SeekableSource`, because they need the ability
to seek to specific offsets in the data. Only the following types of values can
be used where `os.SeekableSource` is required:

- `os.Path`
- `java.nio.channels.SeekableByteChannel`

### os.Generator

Taken from the [geny](https://github.com/lihaoyi/geny) library, `os.Generator`s
are similar to iterators except instead of providing:

- `def hasNext(): Boolean`
- `def next(): T`

`os.Generator`s provide:

- `def generate(handleItem: A => Generator.Action): Generator.Action`

In general, you should not notice much of a difference using `Generator`s vs
using `Iterators`: you can use the same `.map`/`.filter`/`.reduce`/etc.
operations on them, and convert them to collections via the same
`.toList`/`.toArray`/etc. conversions. The main difference is that `Generator`s
can enforce cleanup after traversal completes, so we can ensure open files are
closed and resources are released without any accidental leaks.

### os.PermSet

`os.PermSet`s represent the filesystem permissions on a single file or folder.
Anywhere an `os.PermSet` is required, you can pass in values of these types:

- `java.lang.String`s of the form `"rw-r-xrwx"`, with `r`/`w`/`x` representing
  the permissions that are present or dashes `-` representing the permissions
  which are absent

- Octal `Int`s of the form `Integer.parseInt("777", 8)`, matching the octal
  `755` or `666` syntax used on the command line

- `Set[PosixFilePermission]`

In places where `os.PermSet`s are returned to you, you can then extract the
string, int or set representations of the `os.PermSet` via:

- `perms.toInt(): Int`
- `perms.toString(): String`
- `perms.value: Set[PosixFilePermission]`

## Changelog

### 0.1.2

- First release