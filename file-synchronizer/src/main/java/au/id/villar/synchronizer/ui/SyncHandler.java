package au.id.villar.synchronizer.ui;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

class SyncHandler extends CommandLineUIHandler {

	private InputStream in;

	public SyncHandler(boolean verbose, InputStream in, PrintStream out, Path dir1, Path dir2) {
		super(verbose, out, dir1, dir2);
		this.in = in;
	}

	@Override
	public void missingPath(Path existingPath, Path missingPath) {
		missingPath(existingPath, missingPath, true);

		switch(readOption("Possible options:%n" +
						"    [C/c] copy to missing path,%n" +
						"    [R/r] delete existing file,%n" +
						"    [I/i] ignore,%n" +
						"    [A/a] abort%n    ",
				'C', 'c', 'R', 'r', 'I', 'i', 'A', 'a')) {
			case 'C':case 'c':
				try {
					copy(existingPath, missingPath);
					out.printf("COPIED %s to %s%n", existingPath, missingPath);
				} catch (IOException e) {
					out.printf("ERROR: %s%n", e.getMessage());
				}
				break;
			case 'R':case 'r':
				try {
					delete(existingPath);
					out.printf("DELETED %s%n", existingPath);
				} catch (IOException e) {
					out.printf("ERROR: %s%n", e.getMessage());
				}
				break;
			case 'I':case 'i':
				out.printf("IGNORED %s%n", getRelativePath(missingPath));
				break;
			case 'A':case 'a':
				out.printf("Stopped by the user%n");
				System.exit(1);
				break;
		}
	}

	@Override
	public void differentFiles(Path path1, Path path2) {
		differentFiles(path1, path2, true);

		switch(readOption("Possible options:%n" +
						"    [1] preserve file in path1 (" + path1 + "),%n" +
						"    [2] preserve file in path2 (" + path2 + "),%n" +
						"    [I/i] ignore, [A/a] abort%n    ",
				'1', '2', 'I', 'i', 'A', 'a')) {
			case '1':
				try {
					copy(path1, path2);
					out.printf("COPIED %s to %s%n", path1, path2);
				} catch (IOException e) {
					out.printf("ERROR: %s%n", e.getMessage());
				}
				break;
			case '2':
				try {
					copy(path2, path1);
					out.printf("COPIED %s to %s%n", path2, path1);
				} catch (IOException e) {
					out.printf("ERROR: %s%n", e.getMessage());
				}
				break;
			case 'I':case 'i':
				out.printf("IGNORED %s%n", getRelativePath(path1));
				break;
			case 'A':case 'a':
				out.printf("Stopped by the user%n");
				System.exit(1);
				break;
		}
	}

	@Override
	public void errorFixingLastModified(Path path, Exception e) {
		super.errorFixingLastModified(path, e);
		ignoreOrAbort();
	}

	@Override
	public void errorComparingFiles(Path path1, Path path2, Exception e) {
		super.errorComparingFiles(path1, path2, e);
		ignoreOrAbort();
	}

	private void ignoreOrAbort() {
		switch(readOption("[I/i] ignore, [A/a] abort ",
				'I', 'i', 'A', 'a')) {
			case 'I':case 'i':
				out.printf("IGNORED%n");
				break;
			case 'A':case 'a':
				out.printf("Stopped by the user%n");
				System.exit(1);
				break;
		}
	}

	private char readOption(String description, char ... possibleOptions) {
		Arrays.sort(possibleOptions);
		char option = 0;
		while(option == 0) {
			out.printf(description + "--> ");
			String input = readString();
			if(input.length() == 1 && Arrays.binarySearch(possibleOptions, input.charAt(0)) >= 0) {
				option = input.charAt(0);
			} else {
				out.printf("%nUnknown option: '%s' please try again%n%n", input);
			}
		}
		return option;
	}

	private String readString() {
		StringBuilder builder = new StringBuilder();
		try {
			int read;

			long lastInput;
			do {
				lastInput = System.currentTimeMillis();
				read = in.read();
			} while (lastInput + 500 > System.currentTimeMillis());

			while(!isEndOfString(read)) {
				if(isValidChar(read))
					builder.append((char)read);
				read = in.read();
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return builder.toString();
	}

	private boolean isEndOfString(int read) {
		return read == '\n' || read == '\r';
	}

	private boolean isValidChar(int read) {
		return (read >= '0' && read <= '9') || (read >= 'A' && read <= 'Z') || (read >= 'a' && read <= 'z');
	}

	private void delete(Path node) throws IOException {
		if(Files.isDirectory(node)) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(node)) {
				for(Path element: stream) {
					delete(element);
				}
			}
		}
		Files.delete(node);
	}

	private void copy(Path origin, Path destination) throws IOException {
		if(Files.exists(destination))
			delete(destination);
		if(Files.isDirectory(origin)) {
			Files.createDirectories(destination);
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(origin)) {
				for(Path element: stream) {
					copy(element, destination.resolve(element.getFileName()));
				}
			}
		} else {
			Files.copy(origin, destination, StandardCopyOption.COPY_ATTRIBUTES);
		}
	}

}
