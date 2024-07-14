package jasper.component.cron;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jasper.IntegrationTest;
import jasper.config.Props;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.repository.PluginRepository;
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
public class CronScriptIT {

	@Autowired
	Props props;

	@Autowired
	CronScript cronScript;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	Plugin getScriptPlugin(String tag, String language, String script) {
		var plugin = new Plugin();
		plugin.setTag(tag);
		var mapper = new ObjectMapper();
		try {
			plugin.setConfig((ObjectNode) mapper.readTree("""
			{
				"timeoutMs": 30000,
				"format": "json",
				"language": "",
				"script": ""
			}"""));
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
		plugin.getConfig().set("language", TextNode.valueOf(language));
		plugin.getConfig().set("script", TextNode.valueOf(script));
		return plugin;
	}

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
	void testJavaScriptUpperCaseRef() throws Exception {
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
		pluginRepository.save(getScriptPlugin("plugin/script/test", "javascript", upperCaseScript));
		var url = "comment:" + UUID.randomUUID();
		var input = getRef(url, "My Ref", "test", "public", "+plugin/cron", "plugin/script/test");
		refRepository.save(input);

		cronScript.run(input);

		var responses = refRepository.findAll(hasSource(url).and(hasTag("+needle")));
		assertThat(responses.size()).isEqualTo(1);
		var output = responses.get(0);
		assertThat(output.getComment()).isEqualTo("TEST");
	}

	@Test
	void testPythonUpperCaseRef() throws Exception {
		// language=Python
		var upperCaseScript = """
import sys
import json
from uuid import uuid4
ref = json.loads(sys.stdin.read());
output = {
  'url': 'comment:' + str(uuid4()),
  'sources': [ref['url']],
  'title': 'Re: ' + ref['title'],
  'comment': ref['comment'].upper(),
  'tags': ['public', '+needle'],
};
print(json.dumps({
  'ref': [output],
}))
		""";
		pluginRepository.save(getScriptPlugin("plugin/script/test", "python", upperCaseScript));
		var url = "comment:" + UUID.randomUUID();
		var input = getRef(url, "My Ref", "test", "public", "+plugin/cron", "plugin/script/test");
		refRepository.save(input);

		cronScript.run(input);

		var responses = refRepository.findAll(hasSource(url).and(hasTag("+needle")));
		assertThat(responses.size()).isEqualTo(1);
		var output = responses.get(0);
		assertThat(output.getComment()).isEqualTo("TEST");
	}

}
