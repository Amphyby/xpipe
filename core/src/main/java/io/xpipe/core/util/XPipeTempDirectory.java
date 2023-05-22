package io.xpipe.core.util;

import io.xpipe.core.impl.FileNames;
import io.xpipe.core.process.OsType;
import io.xpipe.core.process.ShellControl;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;

public class XPipeTempDirectory {

    public static String getSystemTempDirectory(ShellControl proc) throws Exception {
        return proc.getOsType().getTempDirectory(proc);
    }

    public static String getSubDirectory(ShellControl proc, String... sub) throws Exception {
        var base = proc.getOsType().getTempDirectory(proc);
        var arr = Stream.concat(Stream.of(base, "xpipe"), Arrays.stream(sub)).toArray(String[]::new);
        var dir = FileNames.join(arr);

        var existsCommand = proc.getShellDialect().createFileExistsCommand(proc, dir);
        if (!existsCommand.executeAndCheck()) {
            proc.executeSimpleCommand(
                    proc.getShellDialect().getMkdirsCommand(dir),
                    "Unable to access or create temporary directory " + dir);

            if (proc.getOsType().equals(OsType.LINUX) || proc.getOsType().equals(OsType.MACOS)) {
                proc.executeSimpleBooleanCommand("chmod 777 \"" + dir + "\"");
            }
        }

        return dir;
    }

    public static void clearSubDirectory(ShellControl proc) throws Exception {
        var dir = getSubDirectory(proc);
        if (!proc.executeSimpleBooleanCommand(proc.getShellDialect().getFileDeleteCommand(dir))) {
            throw new IOException("Unable to delete temporary directory " + dir);
        }
    }
}
