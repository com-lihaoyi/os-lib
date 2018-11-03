package os

import java.io.InputStream


object ResourcePath{
  def resource(resRoot: ResourceRoot) = {
    new ResourcePath(resRoot, Array.empty[String])
  }
}

/**
  * Represents path to a resource on the java classpath.
  *
  * Classloaders are tricky: http://stackoverflow.com/questions/12292926
  */
class ResourcePath private[os](val resRoot: ResourceRoot, segments0: Array[String])
  extends BasePathImpl with ReadablePath with SegmentedPath {
  def toSource = new Source.InputStreamSource(
    resRoot.getResourceAsStream(segments.mkString("/")) match{
      case null => throw ResourceNotFoundException(this)
      case stream => stream
    }
  )
  val segments: IndexedSeq[String] = segments0
  type ThisType = ResourcePath
  def last = segments0.last
  override def toString = resRoot.errorName + "/" + segments0.mkString("/")
  protected[this] def make(p: Seq[String], ups: Int) = {
    if (ups > 0){
      throw PathError.AbsolutePathOutsideRoot
    }
    new ResourcePath(resRoot, p.toArray[String])
  }

  def relativeTo(base: ResourcePath) = {
    var newUps = 0
    var s2 = base.segments

    while(!segments0.startsWith(s2)){
      s2 = s2.dropRight(1)
      newUps += 1
    }
    new RelPath(segments0.drop(s2.length), newUps)
  }


  def startsWith(target: ResourcePath) = {
    segments0.startsWith(target.segments)
  }

}

/**
  * Thrown when you try to read from a resource that doesn't exist.
  * @param path
  */
case class ResourceNotFoundException(path: ResourcePath) extends Exception(path.toString)


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

