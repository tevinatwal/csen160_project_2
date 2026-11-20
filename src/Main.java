import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import stillasleep.three.Game3D;

/**
 * Entry point for "Still Asleep".
 *
 * <p>On macOS the graphics library must run on the first thread of the
 * program, which needs a special Java option. Rather than asking whoever runs
 * the game to remember it, this class restarts itself with that option.
 */
public class Main {

    /**
     * Launches the game.
     *
     * <p>On Windows and Linux this simply starts the game. On macOS it first
     * starts a second copy of Java with the option {@code -XstartOnFirstThread},
     * waits for that copy to finish, and then exits.
     *
     * @param args optional "chapter=N" to start at a chapter (0-11)
     * @throws Exception if the relaunch on macOS fails
     */
    public static void main(String[] args) throws Exception {
        // "Headless" tells Java's AWT library not to open its own window or
        // menu bar. The game only uses AWT to paint pictures in memory, and on
        // macOS a real AWT window would fight with the game window.
        System.setProperty("java.awt.headless", "true");

        // Work out whether we are on a Mac and whether this copy of Java was
        // already started on the first thread. When -XstartOnFirstThread is
        // used, Java sets an environment variable whose name ends with this
        // program's process id, so we can look for it.
        boolean mac = System.getProperty("os.name").toLowerCase().contains("mac");
        long pid = ProcessHandle.current().pid();
        boolean onFirstThread = "1".equals(System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + pid));

        if (mac && !onFirstThread) {
            // Build the command line for a second copy of Java:
            // the same java program, plus the option macOS needs.
            List<String> command = new ArrayList<>();
            command.add(System.getProperty("java.home") + "/bin/java");
            command.add("-XstartOnFirstThread");

            // Pass along the options this copy was started with (memory
            // settings and so on). Debugger and agent options are left out
            // because two programs cannot share the same debugger connection.
            for (String option : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (!option.startsWith("-agentlib") && !option.startsWith("-javaagent")) {
                    command.add(option);
                }
            }

            // Use the same classpath (so the game and its libraries are found),
            // run this same class again, and hand over the original arguments.
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add("Main");
            command.addAll(List.of(args));

            // Start the second copy, share this console with it (inheritIO),
            // wait until the player closes the game, then quit with its result.
            Process process = new ProcessBuilder(command).inheritIO().start();
            System.exit(process.waitFor());
        }

        // Not a Mac, or already on the first thread: start the real game.
        Game3D.launch(args);
    }
}
