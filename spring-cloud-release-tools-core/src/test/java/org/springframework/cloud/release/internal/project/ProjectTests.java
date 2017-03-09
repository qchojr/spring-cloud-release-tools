package org.springframework.cloud.release.internal.project;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.release.internal.ReleaserProperties;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/**
 * @author Marcin Grzejszczak
 */
public class ProjectTests {

	@Before
	public void checkOs() {
		Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
	}

	@Test
	public void should_successfully_execute_a_command_when_after_running_there_is_no_html_file_with_unresolved_tag() throws Exception {
		ReleaserProperties properties = new ReleaserProperties();
		properties.getMaven().setBuildCommand("ls -al");
		properties.setWorkingDir(file("/projects/builder/resolved").getPath());
		Project builder = new Project(properties, executor(properties));

		builder.build();

		then(asString(file("/projects/builder/resolved/resolved.log")))
				.contains("file.txt");
	}

	@Test
	public void should_throw_exception_when_after_running_there_is_an_html_file_with_unresolved_tag() throws Exception {
		ReleaserProperties properties = new ReleaserProperties();
		properties.getMaven().setBuildCommand("ls -al");
		properties.setWorkingDir(file("/projects/builder/unresolved").getPath());
		Project builder = new Project(properties, executor(properties));

		thenThrownBy(builder::build).hasMessageContaining("contains a tag that wasn't resolved properly");
	}

	@Test
	public void should_throw_exception_when_command_took_too_long_to_execute() throws Exception {
		ReleaserProperties properties = new ReleaserProperties();
		properties.getMaven().setBuildCommand("sleep 1");
		properties.getMaven().setWaitTimeInMinutes(0);
		properties.setWorkingDir(file("/projects/builder/unresolved").getPath());
		Project builder = new Project(properties, executor(properties));

		thenThrownBy(builder::build).hasMessageContaining("Process waiting time of [0] minutes exceeded");
	}

	@Test
	public void should_successfully_execute_a_deploy_command() throws Exception {
		ReleaserProperties properties = new ReleaserProperties();
		properties.getMaven().setDeployCommand("ls -al");
		properties.setWorkingDir(file("/projects/builder/resolved").getPath());
		Project builder = new Project(properties, executor(properties));

		builder.deploy();

		then(asString(file("/projects/builder/resolved/resolved.log")))
				.contains("file.txt");
	}

	@Test
	public void should_throw_exception_when_deploy_command_took_too_long_to_execute() throws Exception {
		ReleaserProperties properties = new ReleaserProperties();
		properties.getMaven().setDeployCommand("sleep 1");
		properties.getMaven().setWaitTimeInMinutes(0);
		properties.setWorkingDir(file("/projects/builder/unresolved").getPath());
		Project builder = new Project(properties, executor(properties));

		thenThrownBy(builder::deploy).hasMessageContaining("Process waiting time of [0] minutes exceeded");
	}

	@Test
	public void should_successfully_execute_a_publish_docs_command() throws Exception {
		ReleaserProperties properties = new ReleaserProperties();
		properties.getMaven().setPublishDocsCommands(new String[] { "ls -al", "ls -al" });
		properties.setWorkingDir(file("/projects/builder/resolved").getPath());
		TestProcessExecutor executor = executor(properties);
		Project builder = new Project(properties, executor);

		builder.publishDocs();

		then(asString(file("/projects/builder/resolved/resolved.log")))
				.contains("file.txt");
		then(executor.counter).isEqualTo(2);
	}

	@Test
	public void should_throw_exception_when_publish_docs_command_took_too_long_to_execute() throws Exception {
		ReleaserProperties properties = new ReleaserProperties();
		properties.getMaven().setPublishDocsCommands(new String[] { "ls -al", "ls -al" });
		properties.getMaven().setWaitTimeInMinutes(0);
		properties.setWorkingDir(file("/projects/builder/unresolved").getPath());
		Project builder = new Project(properties, executor(properties));

		thenThrownBy(builder::publishDocs).hasMessageContaining("Process waiting time of [0] minutes exceeded");
	}

	@Test
	public void should_successfully_execute_a_bump_versions_command() throws Exception {
		ReleaserProperties properties = new ReleaserProperties();
		properties.setWorkingDir(file("/projects/builder/resolved").getPath());
		Project builder = new Project(properties, executor(properties)) {
			@Override String bumpVersionsCommand() {
				return "%s";
			}
		};

		builder.bumpVersions("ls -al");

		then(asString(file("/projects/builder/resolved/resolved.log")))
				.contains("file.txt");
	}

	@Test
	public void should_throw_exception_when_bump_command_took_too_long_to_execute() throws Exception {
		ReleaserProperties properties = new ReleaserProperties();
		properties.getMaven().setWaitTimeInMinutes(0);
		properties.setWorkingDir(file("/projects/builder/unresolved").getPath());
		Project builder = new Project(properties, executor(properties)) {
			@Override String bumpVersionsCommand() {
				return "echo '%s'";
			}
		};

		thenThrownBy(() -> builder.bumpVersions("1.0.0")).hasMessageContaining("Process waiting time of [0] minutes exceeded");
	}

	private TestProcessExecutor executor(ReleaserProperties properties) {
		return new TestProcessExecutor(properties);
	}

	class TestProcessExecutor extends ProcessExecutor {

		int counter = 0;

		TestProcessExecutor(ReleaserProperties properties) {
			super(properties);
		}

		@Override ProcessBuilder builder(String[] commands, String workingDir) {
			counter++;
			return super.builder(commands, workingDir)
					.redirectOutput(file("/projects/builder/resolved/resolved.log"));
		}
	}

	private File file(String relativePath) {
		try {
			File root = new File(ProjectTests.class.getResource("/").toURI());
			File file = new File(root, relativePath);
			if (!file.exists()) {
				file.createNewFile();
			}
			return file;
		}
		catch (IOException | URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	private String asString(File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()));
	}

}