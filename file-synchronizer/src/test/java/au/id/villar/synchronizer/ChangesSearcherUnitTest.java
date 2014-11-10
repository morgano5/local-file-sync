package au.id.villar.synchronizer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class ChangesSearcherUnitTest {

	Path testRoot;

	@Before
	public void setUp() throws IOException {
		testRoot = createTempDirectory();
	}

	@After
	public void tearDown() throws IOException {
		delete(testRoot);
	}

	@Test
	public void basicTest() throws InterruptedException, IOException {

		Path root1 = Files.createDirectory(testRoot.resolve("root1"));
		Path root2 = Files.createDirectory(testRoot.resolve("root2"));

		createFile(root1, Paths.get("uno"), "UNO");
		createFile(root1, Paths.get("dos"), "DOS");
		createFile(root1, Paths.get("cuatro"), "CUATRO");

		createFile(root2, Paths.get("uno"), "UNO");
		createFile(root2, Paths.get("dos"), "DOS\nDOS");
		createFile(root2, Paths.get("tres"), "TRES");

		ChangesHandler handler = mock(ChangesHandler.class);
		ChangesSearcher searcher = new ChangesSearcher(handler, root1, root2, null);

		searcher.search();

		verify(handler, times(2)).missingPath(any(), any());
		verify(handler, times(2)).comparing(any(), any());
		verify(handler).differentFiles(any(), any());
		verify(handler, never()).errorComparingFiles(any(), any(), any());
		verify(handler, never()).errorFixingLastModified(any(), any());
	}

	@Test
	public void basicDetailedTest() throws InterruptedException, IOException {

		final Path root1 = Files.createDirectory(testRoot.resolve("root1"));
		final Path root2 = Files.createDirectory(testRoot.resolve("root2"));

		createFile(root1, Paths.get("uno"), "UNO");
		createFile(root1, Paths.get("dos"), "DOS");
		createFile(root1, Paths.get("cuatro"), "CUATRO");

		createFile(root2, Paths.get("uno"), "UNO");
		createFile(root2, Paths.get("dos"), "DOS\nDOS");
		createFile(root2, Paths.get("tres"), "TRES");

		ChangesHandler handler = new ChangesHandler() {

			@Override
			public void comparing(Path path1, Path path2) {
				if(root1.resolve("uno").equals(path1)) {
					assertEquals(root2.resolve("uno"), path2);
				} else if(root1.resolve("dos").equals(path1)) {
					assertEquals(root2.resolve("dos"), path2);
				} else {
					throw new AssertionError("path not expected: " + path1);
				}
			}

			@Override
			public void missingPath(Path existingPath, Path missingPath) {
				if(existingPath.equals(root1.resolve("cuatro"))) {
					assertEquals(root2.resolve("cuatro"), missingPath);
				} else if(existingPath.equals(root2.resolve("tres"))) {
					assertEquals(root1.resolve("tres"), missingPath);
				} else {
					throw new AssertionError("existing and missing path not expected: "
							+ existingPath + ", " + missingPath);
				}
			}

			@Override
			public void differentFiles(Path path1, Path path2) {
				if(root1.resolve("dos").equals(path1)) {
					assertEquals(root2.resolve("dos"), path2);
				} else {
					throw new AssertionError("path not expected: " + path1);
				}
			}

			@Override
			public void errorFixingLastModified(Path path, Exception e) {
				throw new AssertionError("unexpected error: " + e);
			}

			@Override
			public void errorComparingFiles(Path path1, Path path2, Exception e) {
				throw new AssertionError("unexpected error: " + e);
			}

		};
		ChangesSearcher searcher = new ChangesSearcher(handler, root1, root2, null);

		searcher.search();

	}

	@Test(expected = InterruptedException.class)
	public void sillyInterruptedTest() throws IOException, InterruptedException {
		Path root1 = Files.createDirectory(testRoot.resolve("root1"));
		Path root2 = Files.createDirectory(testRoot.resolve("root2"));

		createFile(root1, Paths.get("test"), "CONTENT");
		createFile(root2, Paths.get("test"), "CONTENT");

		ChangesHandler handler = mock(ChangesHandler.class);
		ChangesSearcher searcher = new ChangesSearcher(handler, root1, root2, null);

		searcher.setInterrupted(true);
		searcher.search();

	}

	@Test
	public void lastModifiedChangeTest() throws IOException, InterruptedException {
		Path root1 = Files.createDirectory(testRoot.resolve("root1"));
		Path root2 = Files.createDirectory(testRoot.resolve("root2"));

		createFile(root1, Paths.get("test"), "CONTENT");
		createFile(root2, Paths.get("test"), "CONTENT");

		Path file1 = root1.resolve("test");
		Path file2 = root2.resolve("test");

		long lastModified = Files.getLastModifiedTime(file1).to(TimeUnit.SECONDS);
		lastModified -= 1;

		Files.setLastModifiedTime(file1, FileTime.from(lastModified, TimeUnit.SECONDS));
		Files.setLastModifiedTime(file2, FileTime.from(lastModified - 1, TimeUnit.SECONDS));

		assertEquals(lastModified, Files.getLastModifiedTime(file1).to(TimeUnit.SECONDS));
		assertEquals(lastModified - 1, Files.getLastModifiedTime(file2).to(TimeUnit.SECONDS));

		ChangesHandler handler = mock(ChangesHandler.class);
		ChangesSearcher searcher = new ChangesSearcher(handler, root1, root2, null);

		searcher.setFixLastModified(true);
		searcher.setLevel(Level.LAST_MODIFIED);
		searcher.search();

		assertEquals(lastModified, Files.getLastModifiedTime(file1).to(TimeUnit.SECONDS));
		assertEquals(lastModified, Files.getLastModifiedTime(file2).to(TimeUnit.SECONDS));
	}

	@Test
	public void skipTest() throws IOException, InterruptedException {
		Path root1 = Files.createDirectory(testRoot.resolve("root1"));
		Path root2 = Files.createDirectory(testRoot.resolve("root2"));

		createFile(root1, Paths.get("uno"), "UNO");
		createFile(root1, Paths.get("dos"), "DOS");
		createFile(root1, Paths.get("cuatro"), "CUATRO");

		createFile(root2, Paths.get("uno"), "UNO");
		createFile(root2, Paths.get("dos"), "DOS\nDOS");
		createFile(root2, Paths.get("tres"), "TRES");

		ChangesHandler handler = mock(ChangesHandler.class);
		ChangesSearcher searcher = new ChangesSearcher(handler, root1, root2,
				Arrays.asList(Paths.get("dos"), Paths.get("cuatro")));

		searcher.search();

		verify(handler, times(1)).missingPath(any(), any());
		verify(handler, times(1)).comparing(any(), any());
		verify(handler, never()).differentFiles(any(), any());
		verify(handler, never()).errorComparingFiles(any(), any(), any());
		verify(handler, never()).errorFixingLastModified(any(), any());

	}

	@Test(expected = IllegalArgumentException.class)
	public void errorIfOnePathContainsTheOtherTest() {
		ChangesHandler handler = mock(ChangesHandler.class);
		new ChangesSearcher(handler, Paths.get("test", "uno"),
				Paths.get("test", "uno", "dos"), null);
	}

	@Test
	public void notAnErrorIfOnePathContainsTheOtherAndIsExcludedTest() throws IOException, InterruptedException {
		Path root1 = Files.createDirectory(testRoot.resolve("root1"));
		Path root2 = Files.createDirectory(root1.resolve("root2"));

		createFile(root1, Paths.get("uno"), "UNO");
		createFile(root1, Paths.get("dos"), "DOS");
		createFile(root1, Paths.get("cuatro"), "CUATRO");

		createFile(root2, Paths.get("uno"), "UNO");
		createFile(root2, Paths.get("dos"), "DOS\nDOS");
		createFile(root2, Paths.get("tres"), "TRES");

		ChangesHandler handler = mock(ChangesHandler.class);
		ChangesSearcher searcher = new ChangesSearcher(handler, root1, root2,
				Arrays.asList(Paths.get("dos"), Paths.get("cuatro"), root1.relativize(root2)));

		searcher.search();

		verify(handler, times(1)).missingPath(any(), any());
		verify(handler, times(1)).comparing(any(), any());
		verify(handler, never()).differentFiles(any(), any());
		verify(handler, never()).errorComparingFiles(any(), any(), any());
		verify(handler, never()).errorFixingLastModified(any(), any());
	}

	private Path createTempDirectory() throws IOException {
		return Files.createTempDirectory("TEST_au.id.villar.synchronizer");
	}

	private void createFile(Path root, Path node, String content) throws IOException {
		node = root.resolve(node);
		try (BufferedWriter writer = Files.newBufferedWriter(Files.createFile(node))) {
			writer.append(content);
		}
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

}
