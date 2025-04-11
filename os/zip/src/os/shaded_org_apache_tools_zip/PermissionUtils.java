/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

// taken from https://github.com/apache/ant/blob/ecfca50b0133c576021dd088f855ee878a6b8e66/src/main/org/apache/tools/ant/util/PermissionUtils.java
// removed imports not from Java core libraries and code that uses them
package os.shaded_org_apache_tools_zip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * Contains helper methods for dealing with {@link
 * PosixFilePermission} or the traditional Unix mode representation of
 * permissions.
 */
public class PermissionUtils {

    private PermissionUtils() { }

    /**
     * Translates a set of permissions into a Unix stat(2) {@code
     * st_mode} result.
     * @param permissions the permissions
     * @param type the file type
     * @return the "mode"
     */
    public static int modeFromPermissions(Set<PosixFilePermission> permissions,
                                          FileType type) {
        int mode;
        switch (type) {
        case SYMLINK:
            mode = 012;
            break;
        case REGULAR_FILE:
            mode = 010;
            break;
        case DIR:
            mode = 004;
            break;
        default:
            // OTHER could be a character or block device, a socket or a FIFO - so don't set anything
            mode = 0;
            break;
        }
        mode <<= 3;
        mode <<= 3; // we don't support sticky, setuid, setgid
        mode |= modeFromPermissions(permissions, "OWNER");
        mode <<= 3;
        mode |= modeFromPermissions(permissions, "GROUP");
        mode <<= 3;
        mode |= modeFromPermissions(permissions, "OTHERS");
        return mode;
    }

    /**
     * Translates a Unix stat(2) {@code st_mode} compatible value into
     * a set of permissions.
     * @param mode the "mode"
     * @return set of permissions
     */
    public static Set<PosixFilePermission> permissionsFromMode(int mode) {
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        addPermissions(permissions, "OTHERS", mode);
        addPermissions(permissions, "GROUP", mode >> 3);
        addPermissions(permissions, "OWNER", mode >> 6);
        return permissions;
    }

    private static long modeFromPermissions(Set<PosixFilePermission> permissions,
                                            String prefix) {
        long mode = 0;
        if (permissions.contains(PosixFilePermission.valueOf(prefix + "_READ"))) {
            mode |= 4;
        }
        if (permissions.contains(PosixFilePermission.valueOf(prefix + "_WRITE"))) {
            mode |= 2;
        }
        if (permissions.contains(PosixFilePermission.valueOf(prefix + "_EXECUTE"))) {
            mode |= 1;
        }
        return mode;
    }

    private static void addPermissions(Set<PosixFilePermission> permissions,
                                       String prefix, long mode) {
        if ((mode & 1) == 1) {
            permissions.add(PosixFilePermission.valueOf(prefix + "_EXECUTE"));
        }
        if ((mode & 2) == 2) {
            permissions.add(PosixFilePermission.valueOf(prefix + "_WRITE"));
        }
        if ((mode & 4) == 4) {
            permissions.add(PosixFilePermission.valueOf(prefix + "_READ"));
        }
    }

    /**
     * The supported types of files, maps to the {@code isFoo} methods
     * in {@link java.nio.file.attribute.BasicFileAttributes}.
     */
    public enum FileType {
        /** A regular file. */
        REGULAR_FILE,
        /** A directory. */
        DIR,
        /** A symbolic link. */
        SYMLINK,
        /** Something that is neither a regular file nor a directory nor a symbolic link. */
        OTHER;

        /**
         * Determines the file type of a {@link Path}.
         *
         * @param p Path
         * @return FileType
         * @throws IOException if file attributes cannot be read
         */
        public static FileType of(Path p) throws IOException {
            BasicFileAttributes attrs =
                Files.readAttributes(p, BasicFileAttributes.class);
            if (attrs.isRegularFile()) {
                return FileType.REGULAR_FILE;
            } else if (attrs.isDirectory()) {
                return FileType.DIR;
            } else if (attrs.isSymbolicLink()) {
                return FileType.SYMLINK;
            }
            return FileType.OTHER;
        }
    }

    public static int FILE_TYPE_FLAG = 0170000;
}
