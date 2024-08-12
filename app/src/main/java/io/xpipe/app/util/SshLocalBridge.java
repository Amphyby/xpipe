package io.xpipe.app.util;

import io.xpipe.app.beacon.AppBeaconServer;
import io.xpipe.app.core.AppProperties;
import io.xpipe.app.issue.ErrorEvent;
import io.xpipe.core.process.CommandBuilder;
import io.xpipe.core.process.OsType;
import io.xpipe.core.process.ProcessControlProvider;
import io.xpipe.core.process.ShellControl;
import io.xpipe.core.util.XPipeInstallation;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Getter
public class SshLocalBridge {

    private static SshLocalBridge INSTANCE;

    private final Path directory;
    private final int port;
    private final String user;
    @Setter
    private ShellControl runningShell;

    public SshLocalBridge(Path directory, int port, String user) {
        this.directory = directory;
        this.port = port;
        this.user = user;
    }

    public Path getPubHostKey() {
        return directory.resolve("host_key.pub");
    }

    public Path getHostKey() {
        return directory.resolve("host_key");
    }

    public Path getPubIdentityKey() {
        return directory.resolve("identity.pub");
    }

    public Path getIdentityKey() {
        return directory.resolve("identity");
    }

    public Path getConfig() {
        return directory.resolve("sshd_config");
    }

    public static void init() throws Exception {
        if (INSTANCE != null) {
            return;
        }

        try (var sc = LocalShell.getShell().start()) {
            var bridgeDir = AppProperties.get().getDataDir().resolve("ssh_bridge");
            Files.createDirectories(bridgeDir);
            var port = AppBeaconServer.get().getPort() + 1;
            var user = sc.getShellDialect().printUsernameCommand(sc).readStdoutOrThrow();
            INSTANCE = new SshLocalBridge(bridgeDir, port, user);

            var hostKey = INSTANCE.getHostKey();
            if (!sc.getShellDialect().createFileExistsCommand(sc, hostKey.toString()).executeAndCheck()) {
                sc.executeSimpleCommand("ssh-keygen -q -N \"\" -t ed25519 -f \"" + hostKey + "\"");
            }

            var idKey = INSTANCE.getIdentityKey();
            if (!sc.getShellDialect().createFileExistsCommand(sc, idKey.toString()).executeAndCheck()) {
                sc.executeSimpleCommand("ssh-keygen -q -N \"\" -t ed25519 -f \"" + idKey + "\"");
            }

            var config = INSTANCE.getConfig();
            var command = "\"" + XPipeInstallation.getLocalDefaultCliExecutable() + "\" ssh-launch " + sc.getShellDialect().environmentVariable("SSH_ORIGINAL_COMMAND");
            var pidFile = bridgeDir.resolve("sshd.pid");
            var content = """
                          ForceCommand %s
                          PidFile "%s"
                          StrictModes no
                          SyslogFacility USER
                          LogLevel Debug3
                          Port %s
                          PasswordAuthentication no
                          HostKey "%s"
                          PubkeyAuthentication yes
                          AuthorizedKeysFile "%s"
                          """
                    .formatted(command, pidFile.toString(), "" + port, INSTANCE.getHostKey().toString(),  INSTANCE.getPubIdentityKey());;
            Files.writeString(config, content);

            INSTANCE.updateConfig();

            var exec =  getSshd(sc);
            var launchCommand = CommandBuilder.of().addFile(exec).add("-f").addFile(INSTANCE.getConfig().toString()).add("-p", "" + port);
            var control = ProcessControlProvider.get().createLocalProcessControl(true).start();
            control.writeLine(launchCommand.buildFull(control));
            INSTANCE.setRunningShell(control);
        }
    }

    private void updateConfig() throws IOException {
        var file = Path.of(System.getProperty("user.home"), ".ssh", "config");
        if (!Files.exists(file)) {
            return;
        }

        var content = Files.readString(file);
        if (content.contains("xpipe_bridge")) {
            return;
        }

        var updated = content + "\n\n" + """
                                       Host xpipe_bridge
                                           HostName localhost
                                           User "%s"
                                           Port %s
                                           IdentityFile "%s"
                                       """.formatted(port, user, getIdentityKey());
        Files.writeString(file, updated);
    }

    private static String getSshd(ShellControl sc) throws Exception {
        if (OsType.getLocal() == OsType.WINDOWS) {
            return XPipeInstallation.getLocalBundledToolsDirectory().resolve("openssh").resolve("sshd").toString();
        } else {
            var exec = sc.executeSimpleStringCommand(sc.getShellDialect().getWhichCommand("sshd"));
            return exec;
        }
    }

    public static void reset() {
        if (INSTANCE == null) {
            return;
        }

        try {
            INSTANCE.getRunningShell().closeStdin();
        } catch (IOException e) {
            ErrorEvent.fromThrowable(e).omit().handle();
        }
        INSTANCE.getRunningShell().kill();
        INSTANCE = null;
    }
}
