package au.id.villar.synchronizer.ui;

import au.id.villar.synchronizer.ChangesHandler;
import au.id.villar.synchronizer.ChangesSearcher;
import au.id.villar.synchronizer.Level;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class CommandLineUI {

	/**
	 * <p>Main entry for command line processing. options are given through parameter args as follows:</p>
	 * <p><b>--path1=<i>path1</i></b> <i>path1</i> to synchronize with <i>path2</i>. Must be a directory.</p>
	 * <p><b>--path2=<i>path2</i></b> <i>path2</i> to synchronize with <i>path1</i>. Must be a directory..</p>
	 * <p><b>--verbose</b><br>Prints more information.</p>
	 * <p><b>--info</b><br>if this parameter is provided, then no changes will be performed,
	 * just information will be shown.</p>
	 * <p><b>--fixLastModified</b><br>If it turns out two files are identical and they just differ by
	 * their last modified date, the date for the file belonging two the second path specified will be
	 * updated with the date of the file that belongs to the first path.</p>
	 * <p><b>--exclude=<i>path</i></b><br>exclude <i>path</i> from synchronization. <i>path</i> is relative to
	 * both directories.</p>
	 * <p><b>--level=<i>level</i></b><br>level to decide if two files are equal. Possible values are: SIZE
	 * (two files are considered the same if the have the same size) LAST_MODIFIED (two files are considered equal
	 * if they have same size and same last-modified date) and CONTENT (the actual content of the files are
	 * compared, this is the default)</p>
	 *
	 * @param args array of values interpreted as specified above.
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws IOException, InterruptedException {

		PrintStream out = System.out;

		boolean verbose = false;
		boolean info = false;
		boolean fixLastModified = false;
		boolean help = false;
		Set<Path> pathsToSkip = new HashSet<>();
		Path path1 = null;
		Path path2 = null;
		Level level = Level.CONTENT;

		for(String arg: args) {
			switch(arg) {
				case "--verbose": verbose = true; break;
				case "--info": info = true; break;
				case "--help":case "-h": help = true; break;
				case "--fixLastModified": fixLastModified = true; break;
				default:
					if(arg.startsWith("--exclude=")) {
						pathsToSkip.add(Paths.get(arg.substring("--exclude=".length())));
					} else if(arg.startsWith("--level=")) {
						try {
							level = Level.valueOf(arg.substring("--level=".length()));
						} catch (IllegalArgumentException e) {
							printUsage(out);
							throw e;
						}
					} else if(arg.startsWith("--path1=")) {
						path1 = Paths.get(arg.substring("--path1=".length()));
					} else if(arg.startsWith("--path2=")) {
						path2 = Paths.get(arg.substring("--path2=".length()));
					} else {
						printUsage(out);
						throw new IllegalArgumentException("Unknown token: " + arg);
					}
			}
		}

		if(help) {
			printUsage(out);
			return;
		}

		ChangesHandler handler = info?
				new InfoHandler(verbose, System.out, path1, path2):
				new SyncHandler(verbose, System.in, System.out, path1, path2);

		ChangesSearcher searcher =
				new ChangesSearcher(handler, path1, path2, pathsToSkip.size() > 0? pathsToSkip: null);
		searcher.setLevel(level);
		searcher.setFixLastModified(fixLastModified);

		searcher.search();


	}



	private static void printUsage(PrintStream out) {
		out.printf("USAGE: <classNameOrCommandName> [options] path1=path1 path2=path2%n%n" +

				"--path1=path1      path1 to synchronize with path2. Must be a directory.%n%n" +
				"--path2=path2      path2 to synchronize with path1. Must be a directory.%n%n" +
				"--verbose          Prints more information.%n%n" +
				"--info             if this parameter is provided, then no changes will be performed,%n" +
				"                   just information will be shown.%n%n" +
				"--fixLastModified  If it turns out two files are identical and they just differ by their%n" +
				"                   last modified date, the date for the file belonging two the second path%n" +
				"                   specified will be updated with the date of the file that belongs to the%n" +
				"                   first path.%n%n" +
				"--exclude=path     exclude path from synchronization. path is relative to both directories.%n%n" +
				"--level=level      level to decide if two files are equal. Possible values are: SIZE (two files%n" +
				"                   are considered the same if the have the same size) LAST_MODIFIED (two files%n" +
				"                   are considered equal if they have same size and same last-modified date) and%n" +
				"                   CONTENT (the actual content of the files are compared, this is the default)%n%n" +
				"-h, --help         prints this and finishes.%n%n");
	}

}

