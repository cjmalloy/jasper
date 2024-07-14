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
import java.security.NoSuchAlgorithmException;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.assertj.core.api.Assertions.assertThat;

class PythonTest {

	Python vm = new Python();

	@BeforeEach
	public void init() throws IOException {
		var mapper = new ObjectMapper(new YAMLFactory());
		var app = mapper.readValue(new File("src/test/resources/config/application.yml"), ObjectNode.class);
		var props = app.get("jasper");

		var python = System.getenv("JASPER_PYTHON");
		if (isBlank(python)) {
			python = props.get("python").textValue();
		}
		python = python.replaceFirst("^~", System.getProperty("user.home"));
		vm.props = new Props();
		vm.props.setPython(python);
		vm.api = "http://localhost:10344";
	}

	@Test
	void testRunPython() throws IOException, InterruptedException, ScriptException, NoSuchAlgorithmException {
		// language=Python
		var targetScript = """
import sys
print(sys.stdin.read().upper())
		""";
		var input = "test";

		var output = vm.runPython("", targetScript, input, 30_000);

		assertThat(output).isEqualToIgnoringWhitespace("TEST");
	}

	@Test
	void testPythonRequirements() throws IOException, InterruptedException, ScriptException, NoSuchAlgorithmException {
		// language=Python
		var targetScript = """
import sklearn
sklearn.show_versions()
		""";

		var output = vm.runPython("scikit-learn==1.3.2", targetScript, "", 30_000);

		assertThat(output).contains("sklearn: 1.3.2");
	}

}
