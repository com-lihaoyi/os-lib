package os.shaded_org_apache_tools_zip

private[os] object `package` {
  object shim {
    type ZipEntry = os.shaded_org_apache_tools_zip.ZipEntry
    type ZipFile = os.shaded_org_apache_tools_zip.ZipFile
    type ZipOutputStream = os.shaded_org_apache_tools_zip.ZipOutputStream

    object PermissionUtils {
      val permissionsFromMode = os.shaded_org_apache_tools_zip.PermissionUtils.permissionsFromMode
      val modeFromPermissions = os.shaded_org_apache_tools_zip.PermissionUtils.modeFromPermissions
      val FILE_TYPE_FLAG = os.shaded_org_apache_tools_zip.PermissionUtils.FILE_TYPE_FLAG

      type FileType = os.shaded_org_apache_tools_zip.PermissionUtils.FileType
      object FileType {
        val REGULAR_FILE = os.shaded_org_apache_tools_zip.PermissionUtils.FileType.REGULAR_FILE
        val DIR = os.shaded_org_apache_tools_zip.PermissionUtils.FileType.DIR
        val SYMLINK = os.shaded_org_apache_tools_zip.PermissionUtils.FileType.SYMLINK
        val OTHER = os.shaded_org_apache_tools_zip.PermissionUtils.FileType.OTHER

        val of = os.shaded_org_apache_tools_zip.PermissionUtils.FileType.of
      }
    }

    object UnixStat {
      val LINK_FLAG = os.shaded_org_apache_tools_zip.UnixStat.LINK_FLAG
    }
  }
}
