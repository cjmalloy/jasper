package jasper.component.vm;

import io.micrometer.core.annotation.Timed;
import jasper.config.Props;
import jasper.errors.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.TimeUnit;

@Component
public class Python {
	private static final Logger logger = LoggerFactory.getLogger(Python.class);

	@Autowired
	Props props;

	@Value("http://localhost:${server.port}")
	String api;

	// language=Python
	private final String pythonVmWrapperScript = """
import sys, os
import subprocess
timeout_ms = int(sys.argv[1])
env = os.environ.copy()
env['JASPER_API'] = sys.argv[2]
input_data = sys.stdin.read()
target_script, input_string = input_data.split('\\0')
process = subprocess.Popen([sys.executable, '-c', target_script], stdin=subprocess.PIPE, stdout=sys.stdout, stderr=sys.stderr, env=env)
try:
	process.communicate(input=input_string.encode(), timeout=timeout_ms / 1000)
except subprocess.TimeoutExpired:
	process.kill()
	sys.exit(1)
if process.returncode != 0:
	sys.exit(process.returncode)
	""";

	@Timed("jasper.vm")
	public String runPython(String targetScript, String inputString, int timeoutMs) throws IOException, InterruptedException, ScriptException {
		var processBuilder = new ProcessBuilder(props.getPython(), "-c", pythonVmWrapperScript, ""+timeoutMs, api, props.getCacheApi());
		var process = processBuilder.start();
		try (var writer = new OutputStreamWriter(process.getOutputStream())) {
			writer.write(targetScript);
			writer.write("\0"); // null character as delimiter
			writer.write(inputString);
			writer.flush();
		} catch (IOException e) {
			logger.warn("Script terminated before receiving input.");
		}
		var finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
		if (!finished) {
			process.destroy();
			throw new RuntimeException("Script execution timed out");
		}
		var logs = new StringBuilder();
		try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				logger.debug(line);
				logs.append(line).append("\n");
			}
		}
		try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			var exitCode = process.exitValue();
			if (exitCode != 0) {
				throw new ScriptException("Script execution failed with exit code: " + exitCode, logs.toString());
			}
			var output = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line).append("\n");
			}
			return output.toString();
		}
	}
}
