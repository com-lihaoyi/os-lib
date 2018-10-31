package os

import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
  * Primary Python APIs:
  *
  * Popen
  *   X poll
  *   X wait
  *   X communicate
  *   ? send_signal
  *   X terminate
  *   X kill
  *
  *   X stdin
  *   X stdout
  *   X stderr
  *
  *   ? pid
  *   X returncode
  *
  * check_call
  * check_output
  * X call
  */
object proc {
  def apply(command: Seq[Shellable],
            cwd: Path = null,
            env: Map[String, String] = null): Process = ???

  def call(command: Seq[Shellable],
           cwd: Path = null,
           env: Map[String, String] = null,
           stdin: Source): (Array[Byte], Array[Byte], Int) = ???

  def stream(command: Seq[Shellable],
             cwd: Path = null,
             env: Map[String, String] = null,
             stdin: Source,
             onOut: Array[Byte] => Unit,
             onErr: Array[Byte] => Unit): Int = ???
}
abstract class Process(val wrapped: java.lang.Process){
  def out: ProcessStream
  def err: ProcessStream
  def in: java.io.OutputStream

  def isAlive: Boolean = wrapped.isAlive
  def exitCode: Int = wrapped.exitValue()
  def waitFor(): Int = wrapped.waitFor()
  def waitFor(timeout: Long): Option[Int] = {
    if (wrapped.waitFor(timeout, TimeUnit.MILLISECONDS)) Some(exitCode)
    else None
  }

  def destroy(): Unit = wrapped.destroy()
  def destroyForcibly(): Unit = wrapped.destroyForcibly()

  def write(input: Source): Unit
}

trait ProcessStream{
  def stream: InputStream
  def string: String
  def trim: String
  def bytes: Array[Byte]
  def lines: Seq[String]
}