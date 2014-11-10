package au.id.villar.synchronizer.ui;

import java.io.PrintStream;
import java.nio.file.Path;

class InfoHandler extends CommandLineUIHandler {

	public InfoHandler(boolean verbose, PrintStream out, Path dir1, Path dir2) {
		super(verbose, out, dir1, dir2);
	}

	@Override
	public void missingPath(Path existingPath, Path missingPath) {
		missingPath(existingPath, missingPath, verbose);
	}

	@Override
	public void differentFiles(Path path1, Path path2) {
		differentFiles(path1, path2, verbose);
	}

}
