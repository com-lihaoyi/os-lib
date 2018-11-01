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

OS-Lib is a Scala library to provides a convenient, safe and intuitive interface
to common operating system filesystem and subprocess APIs. OS-Lib aims to be a
complete replacement for the `java.nio.file.Files`/`java.nio.file.Paths` and
`java.lang.ProcessBuilder` APIs, providing an API that is concise and simple
while also providing you all the power, flexibility and performance of the
underlying operating system APIs.

- [Getting Started](#getting-started)
  - [Reading & Writing](#reading--writing)
  - [Reading Resources](#reading-resources)
  - [Appending & Over-writing](#appending--over-writing)
  - [Making Folders](#making-folders)
  - [Moving Things Around](#moving-things-around)
  - [Listing & Walking](#listing--walking)
  - [Reading Metadata](#reading-metadata)
  - [Working with Permissions](#working-with-permissions)
  - [Subprocess](#subprocesses)

- [Operations](#operations)

    Reading & Writing Files

    - [os.read](#osread)
    - [os.read.bytes](#osreadbytes)
    - [os.read.lines](#osreadlines)
    - [os.read.lines.iter](#osreadlinesiter)
    - [os.write](#oswrite)
    - [os.write.append](#oswriteappend)
    - [os.write.over](#oswriteover)

    Listing & Walking Files

    - [os.list](#oslist)
    - [os.list.iter](#oslistiter)
    - [os.walk](#oswalk)
    - [os.walk.attrs](#oswalkattrs)
    - [os.walk.iter](#oswalkiter)
    - [os.walk.iter.attrs](#oswalkiterattrs)

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

    Reading Filesystem Metadata

    - [os.stat](#osstat)
    - [os.stat.full](#osstatfull)
    - [os.isFile](#osisfile)
    - [os.isDir](#osisdir)
    - [os.isLink](#osislink)
    - [os.size](#ossize)
    - [os.mtime](#osmtime)

    Filesystem Permissions

    - [os.getPerms](#osgetperms)
    - [os.setPerms](#ossetperms)
    - [os.getOwner](#osgetowner)
    - [os.setOwner](#ossetowner)
    - [os.getGroup](#osgetgroup)
    - [os.setGroup](#ossetgroup)

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

## Getting Started

To begin using OS-Lib, first add it as a dependency to your project's build:

```scala
// SBT
"com.lihaoyi" %% "os-lib" % "0.0.1"
// Mill
ivy"com.lihaoyi::os-lib:0.0.1"
```

Once you've added os-lib to your project, you can then begin using it in your
project. All functionality is under the top-level `os` package and can be used
without imports. Here is a quick tour of some of the most commonly-used
functionality:

```scala
// Let's pick our working directory
val wd = os.pwd/'out/'example3

// And make sure it's empty
os.remove.all(wd)
os.makeDir.all(wd)
```

### Reading & Writing

```scala
// Reading and writing to files is done through the `os.read` and
// `os.write` You can write `Strings`, `Traversable[String]`s or `Array[Byte]`s
os.write(wd/"file1.txt", "I am cow")
os.write(wd/"file2.txt", Seq("I am cow\n", "hear me moo"))
os.write(wd/'file3, "I weigh twice as much as you".getBytes)

// When reading, you can either `os.read` a `String`, `os.read.lines` to
// get a `Vector[String]` or `os.read.bytes` to get an `Array[Byte]`
os.read(wd/"file1.txt")        ==> "I am cow"
os.read(wd/"file2.txt")        ==> "I am cow\nhear me moo"
os.read.lines(wd/"file2.txt")  ==> Vector("I am cow", "hear me moo")
os.read.bytes(wd/"file3")      ==> "I weigh twice as much as you".getBytes
```

### Reading Resources

```scala
// These operations are mirrored in `read.resource`,
// `read.resource.lines` and `read.resource.bytes` to conveniently read
// files from your classpath:
val resourcePath = os.resource/'test/'os/'folder/"file.txt"
os.read(resourcePath).length        ==> 18
os.read.bytes(resourcePath).length  ==> 18
os.read.lines(resourcePath).length  ==> 1

// You can read resources relative to any particular class, including
// the "current" class by passing in `getClass`
val relResourcePath = os.resource(getClass)/'folder/"file.txt"
os.read(relResourcePath).length        ==> 18
os.read.bytes(relResourcePath).length  ==> 18
os.read.lines(relResourcePath).length  ==> 1

// You can also read `InputStream`s
val inputStream = new java.io.ByteArrayInputStream(
  Array[Byte](104, 101, 108, 108, 111)
)
os.read(inputStream)           ==> "hello"
```
### Appending & Over-writing
```scala
// By default, `os.write` fails if there is already a file in place. Use
// `write.append` or `write.over` if you want to append-to/overwrite
// any existing files
os.write.append(wd/"file1.txt", "\nI eat grass")
os.write.over(wd/"file2.txt", "I am cow\nHere I stand")

os.read(wd/"file1.txt")        ==> "I am cow\nI eat grass"
os.read(wd/"file2.txt")        ==> "I am cow\nHere I stand"

```
### Making Folders
```scala
// You can create folders through `os.makeDir.all`. This behaves the same as
// `mkdir -p` in Bash, and creates and parents necessary
val deep = wd/'this/'is/'very/'deep
os.makeDir.all(deep)
// Writing to a file also creates necessary parents
os.write(deep/'deeeep/"file.txt", "I am cow")
```
### Listing & Walking
```scala
// `os.list` provides a listing of every direct child of the given folder.
// Both files and folders are included
os.list(wd)    ==> Seq(wd/"file1.txt", wd/"file2.txt", wd/'file3, wd/'this)

// `os.walk` does the same thing recursively
os.walk(deep) ==> Seq(deep/'deeeep, deep/'deeeep/"file.txt")

// You can move files or folders with `os.move` and remove them with `os.remove`
os.list(deep)  ==> Seq(deep/'deeeep)
os.move(deep/'deeeep, deep/'renamed_deeeep)
os.list(deep)  ==> Seq(deep/'renamed_deeeep)

// `os.move.into` lets you move a file into a
// particular folder, rather than to particular path
os.move.into(deep/'renamed_deeeep/"file.txt", deep)
os.list(deep/'renamed_deeeep) ==> Seq()
os.list(deep)  ==> Seq(deep/"file.txt", deep/'renamed_deeeep)
```
### Moving Things Around
```scala
// `os.move.over` lets you move a file to a particular path, but
// if something was there before it stomps over it
os.move.over(deep/"file.txt", deep/'renamed_deeeep)
os.list(deep)  ==> Seq(deep/'renamed_deeeep)
os.read(deep/'renamed_deeeep) ==> "I am cow" // contents from file.txt

// `os.remove` can delete individual files or folders
os.remove(deep/'renamed_deeeep)

// while `os.remove.all` behaves the same as `rm -rf` in Bash, and deletes
// anything: file, folder, even a folder filled with contents.
os.remove.all(deep/'renamed_deeeep)
os.list(deep)  ==> Seq()
```

### Reading Metadata
```scala
// You can stat paths to find out information about any file or
// folder that exists there
val info = os.stat(wd/"file1.txt")
info.isDir  ==> false
info.isFile ==> true
info.size   ==> 20
info.name   ==> "file1.txt"

// Apart from `os.stat`, there are also methods to provide the individual
// bits of information you want
os.isDir(wd/"file1.txt")  ==> false
os.isFile(wd/"file1.txt") ==> true
os.size(wd/"file1.txt")   ==> 20

// You can also use `os.stat.full` which provides more information
val fullInfo = os.stat.full(wd/"file1.txt")
fullInfo.ctime: FileTime
fullInfo.atime: FileTime
fullInfo.group: GroupPrincipal
```

### Working with Permissions
```scala
// `os.getPerms`/`os.setPerms` can be used to modify the filesystem
// permissions of a file or folder, by passing in a permissions string:
os.setPerms(wd/"file1.txt", "rwxrwxrwx")
os.getPerms(wd/"file1.txt").toString() ==> "rwxrwxrwx"

// or a permissions integer
os.setPerms(wd/"file1.txt", Integer.parseInt("777", 8))
os.getPerms(wd/"file1.txt").toInt() ==> Integer.parseInt("777", 8)

// `os.getOwner`/`os.setOwner`/`os.getGroup`/`os.setGroup` let you
// inspect and modify ownership of files and folders
val owner = os.getOwner(wd/"file1.txt")
os.setOwner(wd/"file1.txt", owner)
val group = os.getGroup(wd/"file1.txt")
os.setGroup(wd/"file1.txt", group)
```

### Subprocesses
```scala
// `os.proc.call()` cal be used to spawn subprocesses, by passing in
// a sequence of strings making up the subprocess command:
val res = os.proc('echo, "abc").call()
val listed = res.out.string
assert(listed == "abc\n")

// You can also pass in specific files you wish to invoke in the
// subprocess, and customize it's environment, working directory, etc.
val res2 = os.proc(os.root/'bin/'bash, "-c", "echo 'Hello'$ENV_ARG")
             .call(env = Map("ENV_ARG" -> "123"))

assert(res2.out.string.trim == "Hello123")
```


## Operations

### Reading & Writing Files

#### os.read

```scala
os.read(arg: os.Source): String
os.read(arg: os.Source, charSet: Codec): String
os.read(arg: os.SeekableSource,
        offset: Long = 0,
        count: Int = Int.MaxValue,
        charSet: Codec = java.nio.charset.StandardCharsets.UTF_8): String
```

Reads the contents of a [os.Path](#ospath) or other [os.Source](#ossource) as a
`java.lang.String`. Defaults to reading the entire file as UTF-8, but you can
also select a different `charSet` to use, and provide an `offset`/`count` to
read from if the source supports seeking.

#### os.read.bytes

```scala
os.read.bytes(arg: os.Source): Array[Byte] 
os.read.bytes(arg: os.SeekableSource, offset: Long, count: Int): Array[Byte]
```

Reads the contents of a [os.Path](#ospath) or [os.Source](#ossource) as an
`Array[Byte]`; you can provide an `offset`/`count` to read from if the source
supports seeking.

#### os.read.lines

```scala
os.read.lines(arg: os.Source): IndexedSeq[String]
os.read.lines(arg: os.Source, charSet: Codec): IndexedSeq[String]
```

Reads the given [os.Path](#ospath) or other [os.Source](#ossource) as a string
and splits it into lines; defaults to reading as UTF-8, which you can override
by specifying a `charSet`.

#### os.read.lines.iter

```scala
os.read.lines(arg: os.Source): os.Generator[String]
os.read.lines(arg: os.Source, charSet: Codec): os.Generator[String]
```

Identical to [os.read.lines](#osreadlines), but streams the results back to you
in a [os.Generator](#osgenerator) rather than accumulating them in memory. Useful if the file is
large.

#### os.write

```scala
os.write(target: Path, 
         data: os.Source, 
         perms: PermSet = null, 
         createFolders: Boolean = true): Unit
```

Writes data from the given file or [os.Source](#ossource) to a file at the
target [os.Path](#ospath). You can specify the filesystem permissions of the
newly created file by passing in a [PermSet](#ospermset).

This throws an exception if the file already exists. To over-write or append to
an existing file, see [os.write.over](#oswriteover) or
[os.write.append](#oswriteappend).

By default, this creates any necessary enclosing folders; you can disable this
behavior by setting `createFolders = false`

#### os.write.append

```scala
os.write.append(target: Path,
                data: os.Source,
                perms: PermSet = null,
                createFolders: Boolean = true): Unit
```

Similar to [os.write](#oswrite), except if the file already exists this appends
the written data to the existing file contents.

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
over-writes the existing file contents. You can also pass in an `offset` to the
file you want to write to, and `truncate = false` to avoid truncating the
non-overwritten portion of the file.

### Listing & Walking Files

#### os.list

```scala
os.list(p: Path): IndexedSeq[Path]
```

Returns all the files and folders directly within the given folder. If the given
path is not a folder, raises an error.

#### os.list.iter

```scala
os.list.iter(p: Path): os.Generator[Path]
```

Similar to [os.list](#oslist), except provides a [os.Generator](#osgenerator) of
results rather than accumulating all of them in memory. Useful if the result set
is large.

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

#### os.walk.iter

```scala
os.walk.iter(path: Path,
            skip: Path => Boolean = _ => false,
            preOrder: Boolean = true,
            followLinks: Boolean = false,
            maxDepth: Int = Int.MaxValue): os.Generator[Path]
```


Similar to [os.walk](#oswalk), except returns a [os.Generator](#osgenerator) of
the results rather than accumulating them in memory. Useful if you are walking
very large folder hierarchies, or if you wish to begin processing the output
even before the walk has completed.

#### os.walk.iter.attrs

```scala
os.walk.iter.attrs(path: Path,
                   skip: Path => Boolean = _ => false,
                   preOrder: Boolean = true,
                   followLinks: Boolean = false,
                   maxDepth: Int = Int.MaxValue): os.Generator[Path]
```

Similar to [os.walk.iter](#oswalkiter), except it also provides the filesystem
metadata of every path that it returns. Can save time by allowing you to avoid
querying the filesystem for metadata later.

### Manipulating Files & Folders

#### os.exists

```scala
os.exists(p: Path, followLinks: Boolean = true): Boolean
```

Checks if a file or folder exists at the specified path

#### os.move

```scala
os.move(from: Path, to: Path): Unit
```

Moves a file or folder from one path to another. Errors out if the destination
path already exists, or is within the source path.

#### os.move.into

```scala
os.move.into(from: Path, to: Path): Unit
```

Move the given file or folder *into* the destination folder

#### os.move.over

```scala
os.move.over(from: Path, to: Path): Unit
```

Move a file or folder from one path to another, and *overwrite* any file or
folder than may already be present at that path

#### os.copy

```scala
os.copy(from: Path, to: Path): Unit
```

Copy a file or folder from one path to another. Recursively copies folders with
all their contents. Errors out if the destination path already exists, or is
within the source path.


#### os.copy.into

```scala
os.copy.into(from: Path, to: Path): Unit
```

Copy the given file or folder *into* the destination folder

#### os.copy.over

```scala
os.copy.over(from: Path, to: Path): Unit
```

Similar to [os.copy](#oscopy), but if the destination file already exists then
overwrite it instead of erroring out.

#### os.makeDir

```scala
os.makeDir(path: Path): Unit
os.makeDir(path: Path, perms: PermSet): Unit
```

Create a single directory at the specified path. Optionally takes in a
[PermSet](#ospermset) to specify the filesystem permissions of the created
directory.

Errors out if the directory already exists, or if the parent directory of the
specified path does not exist. To automatically create enclosing directories and
ignore the destination if it already exists, using
[os.makeDir.all](#osmakedirall)

#### os.makeDir.all

```scala
os.makeDir.all(path: Path): Unit
os.makeDir.all(path: Path, perms: PermSet): Unit
```

Similar to [os.makeDir](#osmakedir), but automatically creates any necessary
enclosing directories if they do not exist, and does not raise an error if the
destination path already containts a directory.

#### os.remove

```scala
os.remove(target: Path): Unit
```

Remove the target file or folder. Folders need to be empty to be removed; if you
want to remove a folder tree recursively, use [os.remove.all](#osremoveall)

#### os.remove.all

```scala
os.remove.all(target: Path): Unit
```

Remove the target file or folder; if it is a folder and not empty, recursively
remove all it's contents before deleting it.

#### os.hardlink

```scala
os.hardlink(src: Path, dest: Path, perms): Unit
```

Create a hardlink from the source path to the destination path

#### os.symlink

```scala
os.symlink(src: Path, dest: Path, perms: PermSet = null): Unit
```

Create a symbolic from the source path to the destination path. Optionally takes
a [PermSet](#ospermset) to customize the filesystem permissions of the symbolic
link.

#### os.followLink

```scala
os.followLink(src: Path): Option[Path]
```

Attempts to any symbolic links in the given path and return the canonical path.
Returns `None` if the path cannot be resolved (i.e. some symbolic link in the
current path is broken)

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
like, and a [PermSet](#ospermset) to customize its filesystem permissions.

Passing in a [os.Source](#ossource) will initialize the contents of that file to
the provided data; otherwise it is created empty.

By default, temporary files are deleted on JVM exit. You can disable that
behavior by setting `deleteOnExit = false`


#### os.temp.dir

```scala
os.temp.dir(dir: Path = null,
            prefix: String = null,
            deleteOnExit: Boolean = true,
            perms: PermSet = null): Path
```


Creates a temporary directory. You can optionally provide a `dir` to specify
where this file lives, a `prefix` to customize what it looks like, and a
[PermSet](#ospermset) to customize its filesystem permissions.

By default, temporary directories are deleted on JVM exit. You can disable that
behavior by setting `deleteOnExit = false`

### Reading Filesystem Metadata

#### os.stat

```scala
os.stat(p: os.Path, followLinks: Boolean = true): os.StatInfo
```

Reads in the basic filesystem metadata for the given file. By default follows
symbolic links to read the metadata of whatever the link is pointing at; set
`followLinks = false` to disable that and instead read the metadata of the
symbolic link itself.

#### os.stat.full

```scala
os.stat.full(p: os.Path, followLinks: Boolean = true): os.FullStatInfo
```

Reads in the full filesystem metadata for the given file. By default follows
symbolic links to read the metadata of whatever the link is pointing at; set
`followLinks = false` to disable that and instead read the metadata of the
symbolic link itself.

#### os.isFile

```scala
os.isFile(p: Path, followLinks: Boolean = true): Boolean
```

Returns `true` if the given path is a file. Follows symbolic links by default,
pass in `followLinks = false` to not do so.

#### os.isDir

```scala
os.isDir(p: Path, followLinks: Boolean = true): Boolean
```

Returns `true` if the given path is a folder. Follows symbolic links by default,
pass in `followLinks = false` to not do so.

#### os.isLink


```scala
os.isLink(p: Path, followLinks: Boolean = true): Boolean
```

Returns `true` if the given path is a symbolic link. Follows symbolic links by
default, pass in `followLinks = false` to not do so.


#### os.size

```scala
os.size(p: Path): Long
```

Returns the size of the given file, in bytes

#### os.mtime

```scala
os.mtime(p: Path): Long
```

Returns the last-modified timestamp of the given file, in milliseconds

### Filesystem Permissions

#### os.getPerms

```scala
os.getPerms(p: Path, followLinks: Boolean = true): PermSet
```

Reads the filesystem permissions of the given file or folder, as a
[PermSet](#ospermset).

#### os.setPerms

```scala
os.setPerms(p: Path, arg2: PermSet): Unit
```

Sets the filesystem permissions of the given file or folder, as a
[PermSet](#ospermset).

#### os.getOwner

```scala
os.getOwner(p: Path, followLinks: Boolean = true): UserPrincipal
```

Reads the owner of the given file or folder.

#### os.setOwner

```scala
os.setOwner(arg1: Path, arg2: UserPrincipal): Unit
os.setOwner(arg1: Path, arg2: String): Unit
```

Sets the owner of the given file or folder.

#### os.getGroup

```scala
os.getGroup(p: Path, followLinks: Boolean = true): GroupPrincipal
```

Reads the owning group of the given file or folder.

#### os.setGroup

```scala
os.setGroup(arg1: Path, arg2: GroupPrincipal): Unit
os.setGroup(arg1: Path, arg2: String): Unit
```

Sets the owning group of the given file or folder.

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

Often, if you are only interested in manipulating the standard output of the
subprocess, you might set `stderr = os.Inherit` so any error messages are sent
to the console while the main output data is still sent to the parent process
for it to make use of.

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
with however you like.


## Data Types

### os.Path

OS-Lib uses strongly-typed data-structures to represent filesystem paths. The
two basic versions are:

- [os.Path](#ospath): an absolute path, starting from the root
- [os.RelPath](#osrelpath): a relative path, not rooted anywhere


Generally, almost all commands take absolute `os.Path`s. These are basically
defined as:

```scala
class Path private[ops] (val root: java.nio.file.Path, segments0: Array[String])
```

With a number of useful operations that can be performed on them. Absolute paths
can be created in a few ways:

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

