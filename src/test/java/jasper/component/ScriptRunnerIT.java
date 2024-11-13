package jasper.component;

import jasper.IntegrationTest;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.errors.UntrustedScriptException;
import jasper.plugin.config.Script;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jasper.repository.spec.RefSpec.hasSource;
import static jasper.repository.spec.RefSpec.hasTag;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class ScriptRunnerIT {

	@Autowired
	Props props;

	@Autowired
    ScriptRunner scriptRunner;

	@Autowired
	RefRepository refRepository;

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

		scriptRunner.runScripts(input, script);

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

		scriptRunner.runScripts(input, script);

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

		scriptRunner.runScripts(input, script);

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

		scriptRunner.runScripts(input, script);

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

		scriptRunner.runScripts(input, script);

		var responses = refRepository.findAll(hasSource(url).and(hasTag("+needle")));
		assertThat(responses.size()).isEqualTo(1);
		var output = responses.get(0);
		assertThat(output.getComment()).isEqualTo("TEST");
	}

}
