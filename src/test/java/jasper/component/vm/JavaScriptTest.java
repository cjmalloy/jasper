package jasper.component.vm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jasper.config.Props;
import jasper.errors.ScriptException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaScriptTest {

	private static final String PACKAGE_JSON = """
		{
		  "dependencies": {
		    "js-yaml": "4.1.0",
		    "uuid": "11.1.1"
		  },
		  "overrides": {
		    "js-yaml": "4.1.0",
		    "uuid": "11.1.1"
		  }
		}
		""";

	JavaScript vm = new JavaScript();

	@BeforeEach
	public void init() throws IOException {
		var mapper = new ObjectMapper(new YAMLFactory());
		var app = mapper.readValue(new File("src/test/resources/config/application.yml"), ObjectNode.class);
		var props = app.get("jasper");

		var node = System.getenv("JASPER_NODE");
		if (isBlank(node)) {
			node = props.get("node").textValue();
		}
		node = node.replaceFirst("^~", System.getProperty("user.home"));
		var npm = System.getenv("JASPER_NPM");
		if (isBlank(npm)) {
			npm = props.get("npm").textValue();
		}
		npm = npm.replaceFirst("^~", System.getProperty("user.home"));
		vm.props = new Props();
		vm.props.setNode(node);
		vm.props.setNpm(npm);
		vm.api = "http://localhost:10344";
	}

	@Test
	void testRunJavaScript() throws IOException, InterruptedException, ScriptException {
		// language=JavaScript
		var targetScript = """
			console.log(require('fs').readFileSync(0, 'utf-8').toUpperCase());
		""";
		var input = "test";

		var output = vm.runJavaScript(targetScript, input, 30_000);

		assertThat(output).isEqualToIgnoringWhitespace("TEST");
	}

	@Test
	void testRunJavaScriptWithPackages() throws IOException, ScriptException {
		// language=JavaScript
		var targetScript = """
			const yaml = require('js-yaml');
			const uuid = require('uuid');
			console.log(yaml.dump({ id: uuid.v4() }));
		""";

		var output = vm.runJavaScript(PACKAGE_JSON, targetScript, "", 30_000);

		assertThat(output).containsPattern("id: [0-9a-f-]{36}");
	}

	@Test
	void testRunJavaScriptTimeout() {
		// language=JavaScript
		var targetScript = """
            setTimeout(() => { console.log(require('fs').readFileSync(0, 'utf-8').toUpperCase()); }, 2000);
        """;
		var input = "test";

		assertThatThrownBy(() -> vm.runJavaScript(targetScript, input, 1_000))
			.isInstanceOf(ScriptException.class)
			.hasMessageContaining("Script execution timed out");
	}

	@Test
	void testRunJavaScriptError() {
		// language=JavaScript
		var targetScript = """
            console.log(require('fs').readFileSync('non_existent_file', 'utf-8'));
        """;
		var input = "test";

		assertThatThrownBy(() -> vm.runJavaScript(targetScript, input, 30_000))
			.isInstanceOf(ScriptException.class)
			.hasMessageContaining("Script execution failed with exit code:");
	}

	@Test
	void testRunJavaScriptFillStdoutBuffer() {
		// language=JavaScript
		var targetScript = """
            console.log("a".repeat(65_536))
        """;
		var input = "test";

		var future = CompletableFuture.supplyAsync(() -> {
			try {
				return vm.runJavaScript(targetScript, input, 30_000);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		assertThat(future)
			.succeedsWithin(Duration.ofSeconds(2));
	}

}
