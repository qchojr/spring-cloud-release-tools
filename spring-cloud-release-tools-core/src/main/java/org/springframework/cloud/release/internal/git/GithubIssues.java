/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.release.internal.git;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.jcabi.github.Coordinates;
import com.jcabi.github.Github;
import com.jcabi.github.Issue;
import com.jcabi.github.Repo;
import com.jcabi.github.RtGithub;
import com.jcabi.http.wire.RetryWire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.release.internal.ReleaserProperties;
import org.springframework.cloud.release.internal.pom.ProjectVersion;
import org.springframework.cloud.release.internal.pom.Projects;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Marcin Grzejszczak
 */
class GithubIssues {

	private static final Logger log = LoggerFactory.getLogger(GithubIssues.class);

	private static final String GITHUB_ISSUE_TITLE = "Spring Cloud Release took place";

	private final Github github;

	private final ReleaserProperties properties;

	GithubIssues(ReleaserProperties properties) {
		this.github = new RtGithub(new RtGithub(properties.getGit().getOauthToken())
				.entry().through(RetryWire.class));
		this.properties = properties;
	}

	GithubIssues(Github github, ReleaserProperties properties) {
		this.github = github;
		this.properties = properties;
	}

	void fileIssue(Projects projects, ProjectVersion version) {
		if (!this.properties.getGit().isUpdateSpringGuides()) {
			log.info("Will not file an issue to Spring Guides, since the switch to do so "
					+ "is off. Set [releaser.git.update-spring-guides] to [true] to change that");
			return;
		}
		Assert.hasText(this.properties.getGit().getOauthToken(),
				"You have to pass Github OAuth token for milestone closing to be operational");
		// do this only for RELEASE & SR
		String releaseVersion = parsedVersion();
		if (!(version.isRelease() || version.isServiceRelease())) {
			log.info(
					"Guide issue creation will occur only for Release or Service Release versions. Your version is [{}]",
					releaseVersion);
			return;
		}
		Repo springGuides = this.github.repos()
				.get(new Coordinates.Simple("spring-guides", "getting-started-guides"));
		String issueTitle = StringUtils.capitalize(releaseVersion) + " "
				+ GITHUB_ISSUE_TITLE;
		// check if the issue is not already there
		boolean issueAlreadyFiled = issueAlreadyFiled(springGuides, issueTitle);
		if (issueAlreadyFiled) {
			log.info("Issue already filed, will not do that again");
			return;
		}
		try {
			int number = springGuides.issues().create(issueTitle, issueText(projects))
					.number();
			log.info("Successfully created an issue with "
					+ "title [{}] in Spring Guides under: https://github.com/spring-guides/getting-started-guides/issues/"
					+ number, issueTitle);
		}
		catch (IOException e) {
			log.error("Exception occurred while trying to create the issue in guides", e);
		}
	}

	private String parsedVersion() {
		String version = this.properties.getPom().getBranch();
		if (version.startsWith("v")) {
			return version.substring(1);
		}
		return version;
	}

	private String issueText(Projects projects) {
		StringBuilder builder = new StringBuilder().append("Spring Cloud [")
				.append(parsedVersion()).append("] Released with the following projects:")
				.append("\n\n");
		projects.forEach(project -> builder.append(project.projectName).append(" : ")
				.append("`").append(project.version).append("`").append("\n"));
		return builder.toString();
	}

	private boolean issueAlreadyFiled(Repo springGuides, String issueTitle) {
		Map<String, String> map = new HashMap<>();
		map.put("state", "open");
		int counter = 0;
		int maxIssues = 10;
		for (Issue issue : springGuides.issues().iterate(map)) {
			if (counter >= maxIssues) {
				return false;
			}
			Issue.Smart smartIssue = new Issue.Smart(issue);
			try {
				if (issueTitle.equals(smartIssue.title())) {
					return true;
				}
			}
			catch (IOException e) {
				return false;
			}
			counter = counter + 1;
		}
		return false;
	}

}
