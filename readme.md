# OS-Lib [![Build Status][travis-badge]][travis-link] [![Gitter Chat][gitter-badge]][gitter-link] [![Patreon][patreon-badge]][patreon-link]

[travis-badge]: https://travis-ci.org/lihaoyi/os-lib.svg
[travis-link]: https://travis-ci.org/lihaoyi/os-lib
[gitter-badge]: https://badges.gitter.im/Join%20Chat.svg
[gitter-link]: https://gitter.im/lihaoyi/os-lib?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge
[patreon-badge]: https://img.shields.io/badge/patreon-sponsor-ff69b4.svg
[patreon-link]: https://www.patreon.com/lihaoyi

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

// Chain multiple subprocess' stdin/stdout together
val curl = os.proc("curl", "-L" , "https://git.io/fpvpS").spawn(stderr = os.Inherit)
val gzip = os.proc("gzip", "-n").spawn(stdin = curl.stdout)
val sha = os.proc("shasum", "-a", "256").spawn(stdin = gzip.stdout)
sha.stdout.trim ==> "acc142175fa520a1cb2be5b97cbbe9bea092e8bba3fe2e95afa645615908229e  -"
```

OS-Lib is a simple Scala interface to common OS filesystem and subprocess APIs.
OS-Lib aims to make working with files and processes in Scala as simple as any
scripting language, while still providing the safety, flexibility and
performance you would expect from Scala.

OS-Lib aims to be a complete replacement for the
`java.nio.file.Files`/`java.nio.file.Paths`, `java.lang.ProcessBuilder`
`scala.io` and `scala.sys` APIs. You should not need to drop down to underlying
Java APIs, as OS-Lib exposes all relevant capabilities in an intuitive and
performant way. OS-Lib has no dependencies and is unopinionated: it exposes the
underlying APIs is a concise but straightforward way, without introducing it's
own idiosyncrasies, quirks, or clever DSLs.

If you use OS-Lib and like it, please support it by donating to our Patreon:

- [https://www.patreon.com/lihaoyi](https://www.patreon.com/lihaoyi)

- [Getting Started](#getting-started)
- [Cookbook](#cookbook)
    - [Concatenate text files](#concatenate-text-files)
    - [Spawning a subprocess on multiple files](#spawning-a-subprocess-on-multiple-files)
    - [Curl URL to temporary file](#curl-url-to-temporary-file)
    - [Recursive line count](#recursive-line-count)
    - [Largest three files](#largest-three-files)
    - [Moving files out of folder](#moving-files-out-of-folder)
    - [Calculate word frequencies](#calculate-word-frequencies)

- [Operations](#operations)

    Reading & Writing

    - [os.read](#osread)
    - [os.read.bytes](#osreadbytes)
    - [os.read.chunks](#osreadchunks)
    - [os.read.lines](#osreadlines)
    - [os.read.lines.stream](#osreadlinesstream)
    - [os.read.inputStream](#osreadinputstream)
    - [os.write](#oswrite)
    - [os.write.append](#oswriteappend)
    - [os.write.over](#oswriteover)
    - [os.write.outputStream](#oswriteoutputstream)
    - [os.truncate](#ostruncate)

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
"com.lihaoyi" %% "os-lib" % "0.2.5"
// Mill
ivy"com.lihaoyi::os-lib:0.2.5"
// Ammonite
import $ivy.`com.lihaoyi::os-lib:0.2.5`
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

### Spawning a subprocess on multiple files

```scala
// Find and concatenate all .txt files directly in the working directory using `cat`
os.proc("cat", os.list(wd).filter(_.ext == "txt")).call(stdout = wd/"all.txt")

os.read(wd/"all.txt") ==>
  """I am cowI am cow
    |Hear me moo
    |I weigh twice as much as you
    |And I look good on the barbecue""".stripMargin
```

### Curl URL to temporary file

```scala
// Curl to temporary file
val temp = os.temp()
os.proc("curl", "-L" , "https://git.io/fpfTs").call(stdout = temp)

os.size(temp) ==> 53814

// Curl to temporary file
val temp2 = os.temp()
val proc = os.proc("curl", "-L" , "https://git.io/fpfTJ").spawn()

os.write.over(temp2, proc.stdout)
os.size(temp2) ==> 53814
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
#### os.read.chunks

```scala
os.read.chunks(p: ReadablePath, chunkSize: Int): os.Generator[(Array[Byte], Int)]
os.read.chunks(p: ReadablePath, buffer: Array[Byte]): os.Generator[(Array[Byte], Int)]
```

Reads the contents of the given path in chunks of the given size;
returns a generator which provides a byte array and an offset into that
array which contains the data for that chunk. All chunks will be of the
given size, except for the last chunk which may be smaller.

Note that the array returned by the generator is shared between each
callback; make sure you copy the bytes/array somewhere else if you want
to keep them around.

Optionally takes in a provided input `buffer` instead of a `chunkSize`,
allowing you to re-use the buffer between invocations.

```scala
val chunks = os.read.chunks(wd / "File.txt", chunkSize = 2)
  .map{case (buf, n) => buf.take(n).toSeq } // copy the buffer to save the data
  .toSeq

chunks ==> Seq(
  Seq[Byte]('I', ' '),
  Seq[Byte]('a', 'm'),
  Seq[Byte](' ', 'c'),
  Seq[Byte]('o', 'w')
)
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

#### os.read.inputStream

```scala
os.read.inputStream(p: ReadablePath): java.io.InputStream
```

Opens a `java.io.InputStream` to read from the given file.

```scala
val is = os.read.inputStream(wd / "File.txt") // ==> "I am cow"
is.read() ==> 'I'
is.read() ==> ' '
is.read() ==> 'a'
is.read() ==> 'm'
is.read() ==> ' '
is.read() ==> 'c'
is.read() ==> 'o'
is.read() ==> 'w'
is.read() ==> -1
is.close()
```

#### os.write

```scala
os.write(target: Path, 
         data: os.Source, 
         perms: PermSet = null, 
         createFolders: Boolean = false): Unit
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
                createFolders: Boolean = false): Unit
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
              createFolders: Boolean = false,
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

#### os.write.outputStream

```scala
os.write.outputStream(target: Path,
                      perms: PermSet = null,
                      createFolders: Boolean = false,
                      openOptions: Seq[OpenOption] = Seq(CREATE, WRITE))
```

Open a `java.io.OutputStream` to write to the given file.

```scala
val out = os.write.outputStream(wd / "New File.txt")
out.write('H')
out.write('e')
out.write('l')
out.write('l')
out.write('o')
out.close()

os.read(wd / "New File.txt") ==> "Hello"
```

#### os.truncate

```scala
os.truncate(p: Path, size: Long): Unit
```

Truncate the given file to the given size. If the file is smaller than the
given size, does nothing.

```scala
os.read(wd / "File.txt") ==> "I am cow"

os.truncate(wd / "File.txt", 4)
os.read(wd / "File.txt") ==> "I am"
```

### Listing & Walking

#### os.list

```scala
os.list(p: Path): IndexedSeq[Path]
os.list(p: Path, sort: Boolean = true): IndexedSeq[Path]
```

Returns all the files and folders directly within the given folder. If the given
path is not a folder, raises an error. Can be called via
[os.list.stream](#osliststream) to stream the results. To list files recursively,
use [os.walk](#oswalk).

For convenience `os.list` sorts the entries in the folder before returning
them. You can disable sorted by passing in the flag `sort = false`.

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
        maxDepth: Int = Int.MaxValue,
        includeTarget: Boolean = false): IndexedSeq[Path]
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

`os.walk` does not include the path given to it as part of the traversal by
default. Pass in `includeTarget = true` to make it do so. The path appears at
the start of the traversal of `preOrder = true`, and at the end of the traversal
if `preOrder = false`.

```scala
os.walk(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")

os.walk(wd / "folder1", includeTarget = true) ==> Seq(
  wd / "folder1",
  wd / "folder1" / "one.txt"
)

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
              skip: (Path, os.BasicStatInfo) => Boolean = (_, _) => false,
              preOrder: Boolean = true,
              followLinks: Boolean = false,
              maxDepth: Int = Int.MaxValue,
              includeTarget: Boolean = false): IndexedSeq[(Path, os.BasicStatInfo)]
```

Similar to [os.walk](#oswalk), except it also provides the `os.BasicStatInfo`
filesystem metadata of every path that it returns. Can save time by allowing you
to avoid querying the filesystem for metadata later. Note that
`os.BasicStatInfo` does not include filesystem ownership and permissions data;
use `os.stat` on the path if you need those attributes.

```scala
val filesSortedBySize = os.walk.attrs(wd / "misc", followLinks = true)
  .sortBy{case (p, attrs) => attrs.size}
  .collect{case (p, attrs) if attrsisFile => p}

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
              maxDepth: Int = Int.MaxValue,
              includeTarget: Boolean = false): os.Generator[Path]
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
                     skip: (Path, os.BasicStatInfo) => Boolean = (_, _) => false,
                     preOrder: Boolean = true,
                     followLinks: Boolean = false,
                     maxDepth: Int = Int.MaxValue,
                     includeTarget: Boolean = false): os.Generator[(Path, os.BasicStatInfo)]
```

Similar to [os.walk.stream](#oswalkstream), except it also provides the filesystem
metadata of every path that it returns. Can save time by allowing you to avoid
querying the filesystem for metadata later.

```scala
def totalFileSizes(p: os.Path) = os.walk.stream.attrs(p)
  .collect{case (p, attrs) if attrs.isFile => attrs.size}
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
os.makeDir.all(path: Path,
               perms: PermSet = null,
               acceptLinkedDirectory: Boolean = true): Unit
```

Similar to [os.makeDir](#osmakedir), but automatically creates any necessary
enclosing directories if they do not exist, and does not raise an error if the
destination path already containts a directory. Also does not raise an error if
the destination path contains a symlink to a directory, though you can force it
to error out in that case by passing in `acceptLinkedDirectory = false`

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

When removing symbolic links, it is the link that gets removed, and not it's
destination:

```scala
os.remove(wd / "misc" / "file-symlink")
os.exists(wd / "misc" / "file-symlink", followLinks = false) ==> false
os.exists(wd / "File.txt", followLinks = false) ==> true

os.remove(wd / "misc" / "folder-symlink")
os.exists(wd / "misc" / "folder-symlink", followLinks = false) ==> false
os.exists(wd / "folder1", followLinks = false) ==> true
os.exists(wd / "folder1" / "one.txt", followLinks = false) ==> true

os.remove(wd / "misc" / "broken-symlink")
os.exists(wd / "misc" / "broken-symlink", followLinks = false) ==> false
```

If you wish to remove the destination of a symlink, use
[os.readLink](#osreadlink).

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

When removing symbolic links, it is the links that gets removed, and not it's
destination:

```scala
os.remove.all(wd / "misc" / "file-symlink")
os.exists(wd / "misc" / "file-symlink", followLinks = false) ==> false
os.exists(wd / "File.txt", followLinks = false) ==> true

os.remove.all(wd / "misc" / "folder-symlink")
os.exists(wd / "misc" / "folder-symlink", followLinks = false) ==> false
os.exists(wd / "folder1", followLinks = false) ==> true
os.exists(wd / "folder1" / "one.txt", followLinks = false) ==> true

os.remove.all(wd / "misc" / "broken-symlink")
os.exists(wd / "misc" / "broken-symlink", followLinks = false) ==> false
```

If you wish to remove the destination of a symlink, use
[os.readLink](#osreadlink).

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
os.symlink(link: Path, dest: FilePath, perms: PermSet = null): Unit
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

You can create symlinks with either absolute `os.Path`s or relative `os.RelPath`s:

```scala
os.symlink(wd / "File.txt", os.rel/ "Linked2.txt")
os.exists(wd / "Linked2.txt")
os.read(wd / "Linked2.txt") ==> "I am cow"
os.isLink(wd / "Linked2.txt") ==> true
```

Creating absolute and relative symlinks respectively. Relative symlinks are
resolved relative to the enclosing folder of the link.

#### os.readLink

```scala
os.readLink(src: Path): os.FilePath
os.readLink.absolute(src: Path): os.Path
```

Returns the immediate destination of the given symbolic link.

```scala
os.readLink(wd / "misc" / "file-symlink") ==> os.up / "File.txt"
os.readLink(wd / "misc" / "folder-symlink") ==> os.up / "folder1"
os.readLink(wd / "misc" / "broken-symlink") ==> os.rel / "broken"
os.readLink(wd / "misc" / "broken-abs-symlink") ==> os.root / "doesnt" / "exist"
```

Note that symbolic links can be either absolute `os.Path`s or relative
`os.RelPath`s, represented by `os.FilePath`. You can also use `os.readLink.all`
to automatically resolve relative symbolic links to their absolute destination:

```scala
os.readLink.absolute(wd / "misc" / "file-symlink") ==> wd / "File.txt"
os.readLink.absolute(wd / "misc" / "folder-symlink") ==> wd / "folder1"
os.readLink.absolute(wd / "misc" / "broken-symlink") ==> wd / "misc" / "broken"
os.readLink.absolute(wd / "misc" / "broken-abs-symlink") ==> os.root / "doesnt" / "exist"
```



#### os.followLink

```scala
os.followLink(src: Path): Option[Path]
```

Attempts to any deference symbolic links in the given path, recursively, and return the
canonical path. Returns `None` if the path cannot be resolved (i.e. some
symbolic link in the given path is broken)

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
`stdin`/`stdout`/`stderr` streams via `os.ProcessInput` or `os.ProcessOutput`
values: whether to inherit them from the parent process, stream them into
buffers, or output them to files. The following values are common to both input
and output:

- `os.Pipe`: the default, this connects the subprocess's stream to the parent
  process via pipes; if used on its stdin this lets the parent process write to
  the subprocess via `os.SubProcess#stdin`, and if used on its stdout it lets the
  parent process read from the subprocess via `os.SubProcess#stdout`
  and `os.SubProcess#stderr`.

- `os.Inherit`: inherits the stream from the parent process. This lets the
  subprocess read directly from the paren process's standard input or write
  directly to the parent process's standard output or error

- `os.Path`: connects the subprocess's stream to the given filesystem
  path, reading it's standard input from a file or writing it's standard
  output/error to the file.

In addition, you can pass any [os.Source](#ossource)s to a Subprocess's `stdin`
(`String`s, `InputStream`s, `Array[Byte]`s, ...), and pass in a
`os.ProcessOutput` value to `stdout`/`stderr` to register callbacks that are run
when output is received on those streams.

Often, if you are only interested in capturing the standard output of the
subprocess but want any errors sent to the console, you might set `stderr =
os.Inherit` while leaving `stdout = os.Pipe`.

#### os.proc.call

```scala
os.proc(command: os.Shellable*)
  .call(cwd: Path = null,
        env: Map[String, String] = null,
        stdin: ProcessInput = Pipe,
        stdout: ProcessOutput = Pipe,
        stderr: ProcessOutput = Pipe,
        mergeErrIntoOut: Boolean = false,
        timeout: Long = Long.MaxValue,
        check: Boolean = true,
        propagateEnv: Boolean = true): os.CommandResult
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
- `check`: disable this to avoid throwing an exception if the subprocess fails
  with a non-zero exit code
- `propagateEnv`: disable this to avoid passing in this parent process's
  environment variables to the subprocess

Note that redirecting `stdout`/`stderr` elsewhere means that the respective
`CommandResult#out`/`CommandResult#err` values will be empty.

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


// Non-zero exit codes throw an exception by default
val thrown = intercept[os.SubprocessException]{
  os.proc('ls, "doesnt-exist").call(cwd = wd)
}

assert(thrown.result.exitCode != 0)

// Though you can avoid throwing by setting `check = false`
val fail = os.proc('ls, "doesnt-exist").call(cwd = wd, check = false)

assert(fail.exitCode != 0)


fail.out.string ==> ""

assert(fail.err.string.contains("No such file or directory"))

// You can pass in data to a subprocess' stdin
val hash = os.proc("shasum", "-a", "256").call(stdin = "Hello World")
hash.out.trim ==> "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e  -"

// Taking input from a file and directing output to another file
os.proc("base64").call(stdin = wd / "File.txt", stdout = wd / "File.txt.b64")

os.read(wd / "File.txt.b64") ==> "SSBhbSBjb3c="
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
          onOut: (Array[Byte], Int) => Unit,
          onErr: (Array[Byte], Int) => Unit,
          stdin: ProcessInput = Pipe,
          stdout: ProcessOutput = Pipe,
          stderr: ProcessOutput = Pipe,
          mergeErrIntoOut: Boolean = false,
          timeout: Long = Long.MaxValue,
          propagateEnv: Boolean = true): Int
```

Similar to [os.proc.call](#osproccall), but instead of aggregating the process's
standard output/error streams for you, you pass in `onOut`/`onErr` callbacks to
receive the data as it is generated.

Note that the `Array[Byte]` buffer you are passed in `onOut`/`onErr` are
shared from callback to callback, so if you want to preserve the data make
sure you read the it out of the array rather than storing the array (which
will have its contents over-written next callback.

All calls to the `onOut`/`onErr` callbacks take place on the main thread.
Redirecting `stdout`/`stderr` elsewhere means that the respective
`onOut`/`onErr` callbacks will not be triggered

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
         stdin: os.ProcessInput = os.Pipe,
         stdout: os.ProcessOutput = os.Pipe,
         stderr: os.ProcessOutput = os.Pipe,
         mergeErrIntoOut: Boolean = false,
         propagateEnv: Boolean = true): os.SubProcess
```

The most flexible of the `os.proc` calls, `os.proc.spawn` simply configures and
starts a subprocess, and returns it as a `os.SubProcess`. `os.SubProcess` is a
simple wrapper around `java.lang.Process`, which provides `stdin`, `stdout`, and
`stderr` streams for you to interact with however you like. e.g. You can sending
commands to it's `stdin` and reading from it's `stdout`.

To implement pipes, you can spawn a process, take it's stdout, and pass it
as the stdin of a second spawned process.

Note that if you provide `ProcessOutput` callbacks to `stdout`/`stderr`, the
calls to those callbacks take place on newly spawned threads that execute in
parallel with the main thread. Thus make sure any data processing you do in
those callbacks is thread safe! For simpler cases, it may be easier to use
[os.proc.stream](#osprocstream) which triggers it's `onOut`/`onErr` callbacks
all on the calling thread, avoiding needing to think about multithreading and
concurrency issues.

`stdin`, `stdout` and `stderr` are `java.lang.OutputStream`s and
`java.lang.InputStream`s enhanced with the `.writeLine(s: String)`/`.readLine()`
methods for easy reading and writing of character and line-based data.

```scala
// Start a long-lived python process which you can communicate with
val sub = os.proc("python", "-u", "-c", "while True: print(eval(raw_input()))")
            .spawn(cwd = wd)

// Sending some text to the subprocess
sub.stdin.write("1 + 2")
sub.stdin.writeLine("+ 4")
sub.stdin.flush()
sub.stdout.readLine() ==> "7"

sub.stdin.write("'1' + '2'")
sub.stdin.writeLine("+ '4'")
sub.stdin.flush()
sub.stdout.readLine() ==> "124"

// Sending some bytes to the subprocess
sub.stdin.write("1 * 2".getBytes)
sub.stdin.write("* 4\n".getBytes)
sub.stdin.flush()
sub.stdout.read() ==> '8'.toByte

sub.destroy()

// You can chain multiple subprocess' stdin/stdout together
val curl = os.proc("curl", "-L" , "https://git.io/fpfTs").spawn(stderr = os.Inherit)
val gzip = os.proc("gzip", "-n").spawn(stdin = curl.stdout)
val sha = os.proc("shasum", "-a", "256").spawn(stdin = gzip.stdout)
sha.stdout.trim ==> "acc142175fa520a1cb2be5b97cbbe9bea092e8bba3fe2e95afa645615908229e  -"
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

- `Array[Byte]`
- `java.lang.String` (these are treated as UTF-8)
- `java.io.InputStream`
- `java.nio.channels.SeekableByteChannel`
- Any `TraversableOnce[T]` of the above: e.g. `Seq[String]`,
  `List[Array[Byte]]`, etc.

Some operations only work on `os.SeekableSource`, because they need the ability
to seek to specific offsets in the data. Only the following types of values can
be used where `os.SeekableSource` is required:

- `java.nio.channels.SeekableByteChannel`

You can also convert an `os.Path` or `os.ResourcePath` to an `os.Source` via
`.toSource`.

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

### 0.2.6

- Remove `os.StatInfo#name`, `os.BasicStatInfo#name` and `os.FullStatInfo#name`,
  since it is just the last path segment of the stat call and doesn't properly
  reflect the actual name of the file on disk (e.g. on case-insensitive filesystems)

- `os.walk.attrs` and `os.walk.stream.attrs` now provides a `os.BasicFileInfo`
  to the `skip` predicate.

- Add `os.BasePath#baseName`, which returns the section of the path before the
  `os.BasePath#ext` extension.

### 0.2.5

- New `os.readLink`/`os.readLink.absolute` methods to read the contents of
  symbolic links without dereferencing them.

- New `os.read.chunked(p: Path, chunkSize: Int): os.Generator[(Array[Byte],
  Int)]` method for conveniently iterating over chunks of a file

- New `os.truncate(p: Path, size: Int)` method

- `SubProcess` streams now implement `java.io.DataInput`/`DataOutput` for convenience

- `SubProcess` streams are now synchronized for thread-safety

- `os.write` now has `createFolders` default to `false`

- `os.Generator` now has a `.withFilter` method

- `os.symlink` now allows relative paths

- `os.remove.all` now properly removes broken symlinks, and no longer recurses
  into the symlink's contents

- `os.SubProcess` now implements `java.lang.AutoCloseable`

- New `write.channel` counterpart to `read.channel` (and `write.over.channel`
  and `write.append.channel`)

- `os.PermSet` is now modelled internally as a boxed `Int` for performance, and
  is a case class with proper `equals`/`hashcode`

- `os.read.bytes(arg: Path, offset: Long, count: Int)` no longer leaks open file
  channels

- Reversed the order of arguments in `os.symlink` and `os.hardlink`, to match
  the order of the underlying java NIO functions.

### 0.2.2

- Allow chaining of multiple subprocesses `stdin`/`stdout`

### 0.2.0

- First release
