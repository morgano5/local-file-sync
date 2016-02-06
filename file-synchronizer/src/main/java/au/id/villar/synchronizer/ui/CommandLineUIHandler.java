package au.id.villar.synchronizer.ui;

import au.id.villar.synchronizer.ChangesHandler;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

abstract class CommandLineUIHandler implements ChangesHandler {

	protected final PrintStream out;
	protected final Path dir1;
	protected final Path dir2;
	protected boolean verbose;

	private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	protected CommandLineUIHandler(boolean verbose, PrintStream out, Path dir1, Path dir2) {
		this.verbose = verbose;
		this.out = out;
		this.dir1 = dir1;
		this.dir2 = dir2;
	}

	@Override
	public void comparing(Path path1, Path path2) {
		if(verbose)
			out.printf("COMPARING:     %s%n", dir1.relativize(path1));
	}

	@Override
	public abstract void missingPath(Path existingPath, Path missingPath);

	@Override
	public abstract void differentFiles(Path path1, Path path2);

	@Override
	public void errorFixingLastModified(Path path, Exception e) {
		out.printf("ERROR fixing last modified time: %s%n", e.getMessage());
	}

	@Override
	public void errorComparingFiles(Path path1, Path path2, Exception e) {
		out.printf("error: %s%n", e.getMessage());
	}

	protected void missingPath(Path existingPath, Path missingPath, boolean verbose) {
		Path path = getRelativePath(missingPath);
		Path rootForMissing = missingPath.getRoot();
		Path missingFrom = missingPath.subpath(0, missingPath.getNameCount() - path.getNameCount());
		if(rootForMissing != null) missingFrom = rootForMissing.resolve(missingFrom);
		out.printf("MISSING FILE:  %s    (missing in %s)%n", path, missingFrom);
		if(verbose) {
			out.printf("%n    Existing:%n");
			printFileInfo(existingPath);
			out.println();
		}
	}

	protected void differentFiles(Path path1, Path path2, boolean verbose) {
		Path path = getRelativePath(path1);
		out.printf("SYNC REQUIRED: %s%n", path);
		if(verbose) {
			out.printf("%n    FILE 1:%n");
			printFileInfo(path1);
			out.printf("%n    FILE 2:%n");
			printFileInfo(path2);
			out.println();
		}
	}

	protected Path getRelativePath(Path path) {
		return (path.startsWith(dir1)?
				(path.startsWith(dir2)?
						(dir1.startsWith(dir2)? dir1: dir2):
						dir1):
				dir2).relativize(path);
	}

	protected void printFileInfo(Path path) {
		String lastModified;
		String size;

		try {
			lastModified = dateFormat.format(new Date(Files.getLastModifiedTime(path).toMillis()));
		} catch (IOException e) {
			lastModified = "N/A";
		}
		try {
			size = String.valueOf(Files.size(path)) + " b";
		} catch (IOException e) {
			size = "N/A";
		}

		out.printf("    File:          %s%n    Last modified: %s%n    Size:          %s%n",
				path, lastModified, size);
	}

}
