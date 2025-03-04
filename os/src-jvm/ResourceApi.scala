package os
trait ResourceApi {
  def resource(implicit resRoot: ResourceRoot = Thread.currentThread().getContextClassLoader) = {
    os.ResourcePath.resource(resRoot)
  }

}
