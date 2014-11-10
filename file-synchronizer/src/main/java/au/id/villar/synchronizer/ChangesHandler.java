package au.id.villar.synchronizer;

import java.nio.file.Path;

public interface ChangesHandler {

	void comparing(Path path1, Path path2);

	void missingPath(Path existingPath, Path missingPath);

	void differentFiles(Path path1, Path path2);

	void errorFixingLastModified(Path path, Exception e);

	void errorComparingFiles(Path path1, Path path2, Exception e);
}
