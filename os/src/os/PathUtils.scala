package os

import java.io.InputStream

import scala.io.Codec


/**
  * Represents a possible root where classpath resources can be loaded from;
  * either a [[ResourceRoot.ClassLoader]] or a [[ResourceRoot.Class]]. Resources
  * loaded from classloaders are always loaded via their absolute path, while
  * resources loaded via classes are always loaded relatively.
  */
sealed trait ResourceRoot{
  def getResourceAsStream(s: String): InputStream
  def errorName: String
}
object ResourceRoot{
  private[this] def renderClassloader(cl: java.lang.ClassLoader) = {
    cl.getClass.getName + "@" + java.lang.Integer.toHexString(cl.hashCode())
  }
  implicit def classResourceRoot(cls: java.lang.Class[_]): ResourceRoot = Class(cls)
  case class Class(cls: java.lang.Class[_]) extends ResourceRoot{
    def getResourceAsStream(s: String) = cls.getResourceAsStream(s)
    def errorName = renderClassloader(cls.getClassLoader) + ":" + cls.getName
  }
  implicit def classLoaderResourceRoot(cl: java.lang.ClassLoader): ResourceRoot = ClassLoader(cl)
  case class ClassLoader(cl: java.lang.ClassLoader) extends ResourceRoot{
    def getResourceAsStream(s: String) = cl.getResourceAsStream(s)
    def errorName = renderClassloader(cl)
  }

}

