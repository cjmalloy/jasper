package jasper.component;

import jasper.IntegrationTest;
import jasper.config.Props;
import jasper.component.vm.JavaScript;
import jasper.component.vm.Python;
import jasper.component.vm.Shell;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.errors.OperationForbiddenOnOriginException;
import jasper.errors.ScriptException;
import jasper.errors.UntrustedScriptException;
import jasper.plugin.config.Script;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static jasper.repository.spec.RefSpec.hasSource;
import static jasper.repository.spec.RefSpec.hasTag;
import static java.nio.file.Files.exists;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
public class ScriptRunnerIT {

	@Autowired
	Props props;

	@Autowired
    ScriptRunner scriptRunner;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	IngestPlugin ingestPlugin;

	@Autowired
	ScriptExecutorFactory scriptExecutorFactory;

	@Autowired
	JavaScript javaScript;

	@Autowired
	Python python;

	@Autowired
	Shell shell;

	@TempDir
	Path tempDir;

	Ref getRef(String url, String title, String comment, String ...tags) {
		var ref = new Ref();
		ref.setUrl(url);
		ref.setTitle(title);
		ref.setComment(comment);
		ref.setTags(new ArrayList<>(List.of(tags)));
		return ref;
	}

	@BeforeEach
	void init() {
		props.setNode(props.getNode().replaceFirst("^~", System.getProperty("user.home")));
		refRepository.deleteAll();
		pluginRepository.deleteAll();
	}


	@Test
	void testJavaScriptJson() throws UntrustedScriptException {
		// language=JavaScript
		var upperCaseScript = """
			const fs = require('fs');
			const uuid = require('uuid');
			const ref = JSON.parse(fs.readFileSync(0, 'utf-8'));
			var output = {
			  url: 'comment:' + uuid.v4(),
			  sources: [ref.url],
		  	  title: 'Re: ' + ref.title,
		  	  comment: ref.comment.toUpperCase(),
			  tags: ['public', '+needle'],
			};
			console.log(JSON.stringify({
			  ref: [output],
			}));
		""";
		var script = Script.builder()
			.timeoutMs(30_000)
			.language("javascript")
			.format("json")
			.script(upperCaseScript)
			.build();
		var url = "comment:" + UUID.randomUUID();
		var input = getRef(url, "My Ref", "test", "public");

		scriptRunner.runScripts(input, "plugin/script/uppercase", script);

		var responses = refRepository.findAll(hasSource(url).and(hasTag("+needle")));
		assertThat(responses.size()).isEqualTo(1);
		var output = responses.get(0);
		assertThat(output.getComment()).isEqualTo("TEST");
	}

	@Test
	void testPythonJson() throws UntrustedScriptException {
		// language=Python
		var upperCaseScript = """
import sys
import json
from uuid import uuid4
ref = json.loads(sys.stdin.read())
output = {
  'url': 'comment:' + str(uuid4()),
  'sources': [ref['url']],
  'title': 'Re: ' + ref['title'],
  'comment': ref['comment'].upper(),
  'tags': ['public', '+needle'],
}
print(json.dumps({
  'ref': [output],
}))
		""";
		var script = Script.builder()
			.timeoutMs(30_000)
			.language("python")
			.format("json")
			.script(upperCaseScript)
			.build();
		var url = "comment:" + UUID.randomUUID();
		var input = getRef(url, "My Ref", "test", "public");

		scriptRunner.runScripts(input, "plugin/script/uppercase", script);

		var responses = refRepository.findAll(hasSource(url).and(hasTag("+needle")));
		assertThat(responses.size()).isEqualTo(1);
		var output = responses.get(0);
		assertThat(output.getComment()).isEqualTo("TEST");
	}

	@Test
	void testJavaScriptYaml() throws UntrustedScriptException {
		// language=JavaScript
		var upperCaseScript = """
			const fs = require('fs');
			const yaml = require('js-yaml');
			const uuid = require('uuid');
			const ref = yaml.load(fs.readFileSync(0, 'utf-8'));
			var output = {
			  url: 'comment:' + uuid.v4(),
			  sources: [ref.url],
		  	  title: 'Re: ' + ref.title,
		  	  comment: ref.comment.toUpperCase(),
			  tags: ['public', '+needle'],
			};
			console.log(yaml.dump({
			  ref: [output],
			}));
		""";
		var script = Script.builder()
			.timeoutMs(30_000)
			.language("javascript")
			.format("yaml")
			.script(upperCaseScript)
			.build();
		var url = "comment:" + UUID.randomUUID();
		var input = getRef(url, "My Ref", "test", "public");

		scriptRunner.runScripts(input, "plugin/script/uppercase", script);

		var responses = refRepository.findAll(hasSource(url).and(hasTag("+needle")));
		assertThat(responses.size()).isEqualTo(1);
		var output = responses.get(0);
		assertThat(output.getComment()).isEqualTo("TEST");
	}

	@Test
	void testPythonYaml() throws UntrustedScriptException {
		// language=Python
		var upperCaseScript = """
import sys
import yaml
from uuid import uuid4
ref = yaml.safe_load(sys.stdin.read())
output = {
  'url': 'comment:' + str(uuid4()),
  'sources': [ref['url']],
  'title': 'Re: ' + ref['title'],
  'comment': ref['comment'].upper(),
  'tags': ['public', '+needle'],
}
print(yaml.dump({
  'ref': [output],
}))
		""";
		var script = Script.builder()
			.timeoutMs(30_000)
			.language("python")
			.format("yaml")
			.script(upperCaseScript)
			.build();
		var url = "comment:" + UUID.randomUUID();
		var input = getRef(url, "My Ref", "test", "public");

		scriptRunner.runScripts(input, "plugin/script/uppercase", script);

		var responses = refRepository.findAll(hasSource(url).and(hasTag("+needle")));
		assertThat(responses.size()).isEqualTo(1);
		var output = responses.get(0);
		assertThat(output.getComment()).isEqualTo("TEST");
	}

	@Test
	void testShellJson() throws UntrustedScriptException {
		// language=Bash
		var upperCaseScript = """
			jq --arg uuid $(uuidgen) '{
			  ref: [{
				url: ("comment:" + $uuid),
				title: ("Re: " + .title),
				comment: .comment | ascii_upcase,
				tags: (.tags + ["+needle"]),
				sources: [.url]
			  }]
			}'
		""";
		var script = Script.builder()
			.timeoutMs(30_000)
			.language("shell")
			.format("json")
			.script(upperCaseScript)
			.build();
		var url = "comment:" + UUID.randomUUID();
		var input = getRef(url, "My Ref", "test", "public");

		scriptRunner.runScripts(input, "plugin/script/uppercase", script);

		var responses = refRepository.findAll(hasSource(url).and(hasTag("+needle")));
		assertThat(responses.size()).isEqualTo(1);
		var output = responses.get(0);
		assertThat(output.getComment()).isEqualTo("TEST");
	}

	@Test
	void testUninstallCancelsBunScript() throws Exception {
		var started = tempDir.resolve("bun-started");
		var completed = tempDir.resolve("bun-completed");
		assertUninstallCancels("plugin/script/bun.cancel", started, completed, () -> javaScript.runJavaScript("""
			const fs = require('fs');
			fs.writeFileSync('%s', '');
			await Bun.sleep(10_000);
			fs.writeFileSync('%s', '');
			""".formatted(started, completed), "", 30_000));
	}

	@Test
	void testUninstallCancelsPythonScript() throws Exception {
		var started = tempDir.resolve("python-started");
		var completed = tempDir.resolve("python-completed");
		assertUninstallCancels("plugin/script/python.cancel", started, completed, () -> python.runPython("", """
			import time
			open('%s', 'w').close()
			time.sleep(10)
			open('%s', 'w').close()
			""".formatted(started, completed), "", 30_000));
	}

	@Test
	void testUninstallCancelsBashScript() throws Exception {
		var started = tempDir.resolve("bash-started");
		var completed = tempDir.resolve("bash-completed");
		assertUninstallCancels("plugin/script/bash.cancel", started, completed, () -> shell.runShellScript("""
			touch '%s'
			sleep 10
			touch '%s'
			""".formatted(started, completed), "", 30_000));
	}

	private void assertUninstallCancels(String tag, Path started, Path completed, ScriptExecution execution) throws Exception {
		var plugin = new Plugin();
		plugin.setTag(tag);
		pluginRepository.save(plugin);
		var future = scriptExecutorFactory.run(tag, "", () -> {
			try {
				execution.run();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		var startDeadline = System.nanoTime() + SECONDS.toNanos(5);
		while (!exists(started) && System.nanoTime() < startDeadline) {
			Thread.sleep(10);
		}
		assertThat(started).exists();

		ingestPlugin.delete(tag);

		try {
			future.get(2, SECONDS);
		} catch (ExecutionException expected) {
			assertThat(expected)
				.hasRootCauseInstanceOf(ScriptException.class)
				.hasRootCauseMessage("Script execution interrupted");
		}
		assertThat(completed).doesNotExist();
	}

	@FunctionalInterface
	private interface ScriptExecution {
		void run() throws Exception;
	}

	@Test
	void testUninstalledScriptDoesNotRun() {
		var input = getRef("comment:" + UUID.randomUUID(), "My Ref", "test", "public");

		assertThatThrownBy(() -> scriptRunner.runScripts(input, "plugin/script/not-installed-" + UUID.randomUUID()))
			.isInstanceOf(OperationForbiddenOnOriginException.class);
	}

}
