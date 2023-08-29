package os

import java.util.concurrent.{ArrayBlockingQueue, Semaphore, TimeUnit}
import collection.JavaConverters._
import scala.annotation.tailrec
import java.lang.ProcessBuilder.Redirect
import os.SubProcess.InputStream
import java.io.IOException

/**
 * Convenience APIs around [[java.lang.Process]] and [[java.lang.ProcessBuilder]]:
 *
 * - os.proc.call provides a convenient wrapper for "function-like" processes
 *   that you invoke with some input, whose entire output you need, but
 *   otherwise do not have any intricate back-and-forth communication
 *
 * - os.proc.stream provides a lower level API: rather than providing the output
 *   all at once, you pass in callbacks it invokes whenever there is a chunk of
 *   output received from the spawned process.
 *
 * - os.proc(...) provides the lowest level API: an simple Scala API around
 *   [[java.lang.ProcessBuilder]], that spawns a normal [[java.lang.Process]]
 *   for you to deal with. You can then interact with it normally through
 *   the standard stdin/stdout/stderr streams, using whatever protocol you
 *   want
 */

case class proc(command: Shellable*) {
  def commandChunks = command.flatMap(_.value)

  def pipeTo(next: proc): ProcGroup = ProcGroup(Seq(this, next))

  /**
   * Invokes the given subprocess like a function, passing in input and returning a
   * [[CommandResult]]. You can then call `result.exitCode` to see how it exited, or
   * `result.out.bytes` or `result.err.string` to access the aggregated stdout and
   * stderr of the subprocess in a number of convenient ways. If a non-zero exit code
   * is returned, this throws a [[os.SubprocessException]] containing the
   * [[CommandResult]], unless you pass in `check = false`.
   *
   * If you want to spawn an interactive subprocess, such as `vim`, `less`, or a
   * `python` shell, set all of `stdin`/`stdout`/`stderr` to [[os.Inherit]]
   *
   * `call` provides a number of parameters that let you configure how the subprocess
   * is run:
   *
   * @param cwd             the working directory of the subprocess
   * @param env             any additional environment variables you wish to set in the subprocess
   * @param stdin           any data you wish to pass to the subprocess's standard input
   * @param stdout          How the process's output stream is configured.
   * @param stderr          How the process's error stream is configured.
   * @param mergeErrIntoOut merges the subprocess's stderr stream into it's stdout
   * @param timeout         how long to wait in milliseconds for the subprocess to complete
   * @param check           disable this to avoid throwing an exception if the subprocess
   *                        fails with a non-zero exit code
   * @param propagateEnv    disable this to avoid passing in this parent process's
   *                        environment variables to the subprocess
   */
  def call(
      cwd: Path = null,
      env: Map[String, String] = null,
      stdin: ProcessInput = Pipe,
      stdout: ProcessOutput = Pipe,
      stderr: ProcessOutput = os.Inherit,
      mergeErrIntoOut: Boolean = false,
      timeout: Long = -1,
      check: Boolean = true,
      propagateEnv: Boolean = true
  ): CommandResult = {

    val chunks = new java.util.concurrent.ConcurrentLinkedQueue[Either[geny.Bytes, geny.Bytes]]

    val sub = spawn(
      cwd,
      env,
      stdin,
      if (stdout ne os.Pipe) stdout
      else os.ProcessOutput.ReadBytes((buf, n) =>
        chunks.add(Left(new geny.Bytes(java.util.Arrays.copyOf(buf, n))))
      ),
      if (stderr ne os.Pipe) stderr
      else os.ProcessOutput.ReadBytes((buf, n) =>
        chunks.add(Right(new geny.Bytes(java.util.Arrays.copyOf(buf, n))))
      ),
      mergeErrIntoOut,
      propagateEnv
    )

    sub.join(timeout)

    val chunksSeq = chunks.iterator.asScala.toIndexedSeq
    val res = CommandResult(commandChunks, sub.exitCode(), chunksSeq)
    if (res.exitCode == 0 || !check) res
    else throw SubprocessException(res)
  }

  /**
   * The most flexible of the [[os.proc]] calls, `os.proc.spawn` simply configures
   * and starts a subprocess, and returns it as a `java.lang.Process` for you to
   * interact with however you like.
   *
   * To implement pipes, you can spawn a process, take it's stdout, and pass it
   * as the stdin of a second spawned process.
   *
   * Note that if you provide `ProcessOutput` callbacks to `stdout`/`stderr`,
   * the calls to those callbacks take place on newly spawned threads that
   * execute in parallel with the main thread. Thus make sure any data
   * processing you do in those callbacks is thread safe!
   */
  def spawn(
      cwd: Path = null,
      env: Map[String, String] = null,
      stdin: ProcessInput = Pipe,
      stdout: ProcessOutput = Pipe,
      stderr: ProcessOutput = os.Inherit,
      mergeErrIntoOut: Boolean = false,
      propagateEnv: Boolean = true
  ): SubProcess = {
    val builder = buildProcess(commandChunks, cwd, env, stdin, stdout, stderr, mergeErrIntoOut, propagateEnv)

    val cmdChunks = commandChunks
    val commandStr = cmdChunks.mkString(" ")
    lazy val proc: SubProcess = new SubProcess(
      builder.start(),
      stdin.processInput(proc.stdin).map(new Thread(_, commandStr + " stdin thread")),
      stdout.processOutput(proc.stdout).map(new Thread(_, commandStr + " stdout thread")),
      stderr.processOutput(proc.stderr).map(new Thread(_, commandStr + " stderr thread"))
    )

    proc.inputPumperThread.foreach(_.start())
    proc.outputPumperThread.foreach(_.start())
    proc.errorPumperThread.foreach(_.start())
    proc
  }
}

case class ProcGroup(commands: Seq[proc]) {
  assert(comamnds.size >= 2)

  def pipeTo(next: proc) = ProcGroup(commands :+ next)

  def call(
    cwd: Path = null,
    env: Map[String, String] = null,
    stdin: ProcessInput = Pipe,
    stdout: ProcessOutput = Pipe,
    stderr: ProcessOutput = os.Inherit,
    mergeErrIntoOut: Boolean = false,
    timeout: Long = -1,
    check: Boolean = true,
    propagateEnv: Boolean = true,
    failFast: Boolean = true,
    pipefail: Boolean = true
  ): CommandResult = {
    val chunks = new java.util.concurrent.ConcurrentLinkedQueue[Either[geny.Bytes, geny.Bytes]]

    val sub = spawn(
      cwd,
      env,
      stdin,
      if (stdout ne os.Pipe) stdout
      else os.ProcessOutput.ReadBytes((buf, n) =>
        chunks.add(Left(new geny.Bytes(java.util.Arrays.copyOf(buf, n))))
      ),
      if (stderr ne os.Pipe) stderr
      else os.ProcessOutput.ReadBytes((buf, n) =>
        chunks.add(Right(new geny.Bytes(java.util.Arrays.copyOf(buf, n))))
      ),
      mergeErrIntoOut,
      propagateEnv,
      failFast,
      pipefail
    )

    sub.join(timeout)

    val chunksSeq = chunks.iterator.asScala.toIndexedSeq
    val res = CommandResult(commandChunks, sub.exitCode(), chunksSeq)
    if (res.exitCode == 0 || !check) res
    else throw SubprocessException(res)
  }

  private lazy val isAtLeastJvm9: Boolean = {
    val version = Option(System.getProperty("java.version"))
    val major = version.flatMap(_.split("\\.")(0).toIntOption)
    major.exists(_ >= 9)
  }

  def spawn(
    cwd: Path = null,
    env: Map[String, String] = null,
    stdin: ProcessInput = Pipe,
    stdout: ProcessOutput = Pipe,
    stderr: ProcessOutput = os.Inherit,
    mergeErrIntoOut: Boolean = false,
    propagateEnv: Boolean = true,
    pipefail: Boolean = true
  ): SubProcess = {
    if(isAtLeastJvm9) spawnJvm9(cwd, env, stdin, stdout, stderr, mergeErrIntoOut, propagateEnv, failFast, pipefail)
    else spawnJvmOld(cwd, env, stdin, stdout, stderr, mergeErrIntoOut, propagateEnv, pipefail)
  }

  private def spawnJvm9(
    cwd: Path = null,
    env: Map[String, String] = null,
    stdin: ProcessInput = Pipe,
    stdout: ProcessOutput = Pipe,
    stderr: ProcessOutput = os.Inherit,
    mergeErrIntoOut: Boolean = false,
    propagateEnv: Boolean = true,
    pipefail: Boolean = true
  ): SubProcess = {
    val builders = commands.zipWithIndex.map { 
      // First process in pipeline, we assert that there are at least two proceses in the pipepline,
      // so there is no need to cover the case when the first and the last processes are the same.
      case (proc(command), 0) => 
        (buildProcess(command, cwd, env, stdin, Pipe, stderr, mergeErrIntoOut, propagateEnv),
        (proc: Process) => {
          val proc = new SubProcess(
            proc,
            stdin.processInput(proc.stdin).map(new Thread(_, commandStr + " stdin thread")),
            None, //
            stderr.processOutput(proc.stderr).map(new Thread(_, commandStr + " stderr thread"))
          )
        })
      // Last process in the pipeline
      case (proc(command), commands.length - 1) => 
        (buildProcess(command, cwd, env, Pipe, stdout, stderr, mergeErrIntoOut, propagateEnv),
        (proc: Process) => {
          val proc = new SubProcess(
            proc,
            None,
            stdout.processOutput(proc.stdout).map(new Thread(_, commandStr + " stdout thread")),
            stderr.processOutput(proc.stderr).map(new Thread(_, commandStr + " stderr thread"))
          )
        })
      case (proc(command), index) => 
        (buildProcess(command, cwd, env, Pipe, Pipe, stderr, mergeErrIntoOut, propagateEnv),
        (proc: Process) => {
          val proc = new SubProcess(
            proc,
            None,
            None,
            stderr.processOutput(proc.stderr).map(new Thread(_, commandStr + " stderr thread"))
          )
        })
    }

    val processes: Seq[Process] = ProcessBuilder.startPipeline(builders.asJava).asScala.toSeq
    val subprocesses = builders.zip(processes).map { case ((_, f), proc) => f(proc) }
    subprocesses.flatMap(p => Seq(p.inputPumperThread, p.outputPumperThread, p.errorPumperThread).flatten)
      .foreach(_.start())
    
    ProcessesPipeline(subprocesses, None, pipefail)
  }

  private def wrapWithBrokenPipeHandler(wrapped: ProcessInput, index: Int, queue: LinkedBlockingQueue[Int]) = 
    new ProcessInput {
      override def redirectFrom: Redirect = wrapped.redirectFrom
      override def processInput(stdin: => InputStream): Option[Runnable] = {
        try {
          wrapped.processInput(stdin)
        } catch {
          case _: IOException => queue.put(index)
        }
      }
    }

  private def spawnJvmOld(
    cwd: Path = null,
    env: Map[String, String] = null,
    stdin: ProcessInput = Pipe,
    stdout: ProcessOutput = Pipe,
    stderr: ProcessOutput = os.Inherit,
    mergeErrIntoOut: Boolean = false,
    propagateEnv: Boolean = true,
    pipefail: Boolean = true
  ): SubProcess = {
    val brokenPipeQueue = new LinkedBlockingQueue[Int]()
    val (_, procs) = commands.zipWithIndex.foldLeft((Option.empty[ProcessInput], Seq.empty[SubProcess])) {
      case ((None, _), (proc, _)) =>
        val proc = proc.spawn(cwd, env, stdin, Pipe, stderr, mergeErrIntoOut, propagateEnv)
      case ((Some(input), acc), (proc, index)) if index == commands.length - 1 =>
        val proc = proc.spawn(cwd, env, wrapWithBrokenPipeHandler(input, index, brokenPipeQueue), stdout, stderr, mergeErrIntoOut, propagateEnv)
        (None, acc :+ proc)
      case ((Some(input), acc), (proc, index)) =>
        val proc = proc.spawn(cwd, env, wrapWithBrokenPipeHandler(input, index, brokenPipeQueue), Pipe, stderr, mergeErrIntoOut, propagateEnv)
        (Some(proc.stdout), acc :+ proc)
    }
    val pipeline = ProcessesPipeline(procs, Some(brokenPipeQueue), pipefail)
    pipeline.brokenPipeHandler.foreach(_.start())
    pipeline
  }

}

private[os] object ProcessOps {
  private def buildProcess(
      command: Seq[String],
      cwd: Path = null,
      env: Map[String, String] = null,
      stdin: ProcessInput = Pipe,
      stdout: ProcessOutput = Pipe,
      stderr: ProcessOutput = os.Inherit,
      mergeErrIntoOut: Boolean = false,
      propagateEnv: Boolean = true
  ): ProcessBuilder = {
    val builder = new java.lang.ProcessBuilder()

    val baseEnv =
      if (propagateEnv) sys.env
      else Map()
    for ((k, v) <- baseEnv ++ Option(env).getOrElse(Map())) {
      if (v != null) builder.environment().put(k, v)
      else builder.environment().remove(k)
    }

    builder.directory(Option(cwd).getOrElse(os.pwd).toIO)

    builder
      .command(cmdChunks: _*)
      .redirectInput(stdin.redirectFrom)
      .redirectOutput(stdout.redirectTo)
      .redirectError(stderr.redirectTo)
      .redirectErrorStream(mergeErrIntoOut)
  }
}
