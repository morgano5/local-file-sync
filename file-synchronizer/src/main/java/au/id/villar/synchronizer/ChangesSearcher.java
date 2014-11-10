package au.id.villar.synchronizer;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ChangesSearcher {

	public static final Level DEFAULT_LEVEL = Level.CONTENT;

	private final ChangesHandler handler;
	private final Path dir1;
	private final Path dir2;
	private final Set<Path> pathsToSkipDir1;
	private final Set<Path> pathsToSkipDir2;

	private Level level;
	private boolean fixLastModified;

	private volatile boolean interrupted;

	public ChangesSearcher(ChangesHandler handler, Path dir1, Path dir2, Collection<Path> pathsToSkip) {

		validateDir(dir1);
		validateDir(dir2);
		validateDirRelationship(dir1, dir2, pathsToSkip);
		if(handler == null) {
			throw new IllegalArgumentException("handler can't be null");
		}

		this.handler = handler;
		this.dir1 = dir1;
		this.dir2 = dir2;
		this.pathsToSkipDir1 = initPathsToSkip(pathsToSkip, dir1);
		this.pathsToSkipDir2 = initPathsToSkip(pathsToSkip, dir2);

		this.level = DEFAULT_LEVEL;
		this.fixLastModified = false;
	}

	public void setLevel(Level level) {
		this.level = level;
	}

	public void setFixLastModified(boolean fixLastModified) {
		this.fixLastModified = fixLastModified;
	}

	public void setInterrupted(boolean interrupted) {
		this.interrupted = interrupted;
	}

	public void search() throws IOException, InterruptedException {
		compareDirs(dir1, dir2);
	}

	private void validateDir(Path dir) {
		if(dir == null) {
			throw new IllegalArgumentException("directory path value can't be null");
		}
		if(!Files.isDirectory(dir)) {
			throw new IllegalArgumentException("directory path should be an actual directory");
		}
	}

	private void validateDirRelationship(Path dir1, Path dir2, Collection<Path> exclusions) {
		validateExclusionIfDir1ContainsDir2(dir1, dir2, exclusions);
		validateExclusionIfDir1ContainsDir2(dir2, dir1, exclusions);
	}

	private void validateExclusionIfDir1ContainsDir2(Path dir1, Path dir2, Collection<Path> exclusions) {
		if(!dir2.startsWith(dir1))
			return;
		dir2 = dir1.relativize(dir2);
		for(Path exclusion: exclusions) {
			if(dir2.startsWith(exclusion))
				return;
		}
		throw new IllegalArgumentException("one dir contains the other and is not in the list of skip paths");
	}

	private void compareDirs(Path dir1, Path dir2) throws IOException, InterruptedException {
		List<Path> paths1 = getFilesInDescendingOrder(dir1, pathsToSkipDir1);
		List<Path> paths2 = getFilesInDescendingOrder(dir2, pathsToSkipDir2);

		while(paths1.size() > 0 && paths2.size() > 0 && !interrupted) {
			Path path1 = paths1.remove(paths1.size() - 1);
			Path path2 = paths2.remove(paths2.size() - 1);

			int compared = path1.getFileName().compareTo(path2.getFileName());
			if(compared == 0) {
				handler.comparing(path1, path2);

				boolean path1IsDir = Files.isDirectory(path1);
				boolean path2IsDir = Files.isDirectory(path2);

				if(path1IsDir) {
					if(path2IsDir) {
						compareDirs(path1, path2);
					} else {
						handler.differentFiles(path1, path2);
					}
				} else if(path2IsDir || !filesAreEqual(path1, path2)) {
					handler.differentFiles(path1, path2);
				}

			} else if (compared > 0) {
				paths1.add(path1);
				handler.missingPath(path2, dir1.resolve(path2.getFileName()));
			} else {
				paths2.add(path2);
				handler.missingPath(path1, dir2.resolve(path1.getFileName()));
			}
		}

		while(paths1.size() > 0 && !interrupted) {
			Path path = paths1.remove(paths1.size() - 1);
			handler.missingPath(path, dir2.resolve(path.getFileName()));
		}

		while(paths2.size() > 0 && !interrupted) {
			Path path = paths2.remove(paths2.size() - 1);
			handler.missingPath(path, dir1.resolve(path.getFileName()));
		}

		if(interrupted) {
			throw new InterruptedException();
		}
	}

	private List<Path> getFilesInDescendingOrder(Path dir, Set<Path> pathsToSkip) throws IOException {
		List<Path> files = new ArrayList<>();
		try(DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for(Path node: stream) {
				if(pathsToSkip != null && pathsToSkip.contains(node)) {
					pathsToSkip.remove(node);
				} else {
					files.add(node);
				}
			}
		}
		Collections.sort(files);
		Collections.reverse(files);
		return files;
	}

	private boolean filesAreEqual(Path path1, Path path2) throws IOException, InterruptedException {
		if(Files.size(path1) != Files.size(path2))
			return false;
		if(level == Level.SIZE)
			return true;
		long lastModified = getLastModified(path1);
		boolean sameLastModified = lastModified == getLastModified(path2);
		if(sameLastModified && level == Level.LAST_MODIFIED)
			return true;
		if(contentIsEqual(path1, path2)) {
			if(!sameLastModified && fixLastModified) {
				try {
					Files.setLastModifiedTime(path2, FileTime.from(lastModified, TimeUnit.MILLISECONDS));
				} catch (IOException e)  {
					handler.errorFixingLastModified(path2, e);
				}
			}
			return true;
		} else {
			return false;
		}
	}

	private boolean contentIsEqual(Path path1, Path path2) throws InterruptedException {
		byte[] buffer1 = new byte[2048];
		byte[] buffer2 = new byte[2048];

		int len1;
		int len2;

		try (InputStream stream1 = Files.newInputStream(path1);
				InputStream stream2 = Files.newInputStream(path2)) {
			do {
				len1 = stream1.read(buffer1);
				len2 = stream2.read(buffer2);
				if (len1 != len2 || !arrayEquals(buffer1, buffer2, len1))
					return false;
			} while (len1 != -1 && !interrupted);

			if(interrupted) {
				throw new InterruptedException();
			}

		} catch (IOException e) {
			handler.errorComparingFiles(path1, path2, e);
		}
		return true;
	}

	private boolean arrayEquals(byte[] a1, byte[] a2, int length) {
		for(int x = 0; x < length; x++) {
			if(a1[x] != a2[x])
				return false;
		}
		return true;
	}

	private long getLastModified(Path path) throws IOException {
		return Files.getLastModifiedTime(path).toMillis();
	}

	private Set<Path> initPathsToSkip(Collection<Path> paths, Path root) {
		return paths != null? new HashSet<>(paths.stream().map(root::resolve).collect(Collectors.toList())): null;
	}
}
