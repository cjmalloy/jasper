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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.TimeUnit;

@Component
public class Shell {
	private static final Logger logger = LoggerFactory.getLogger(Shell.class);

	@Autowired
	Props props;

	@Value("http://localhost:${server.port}")
	String api;

	// language=Sh
	private final String wrapperScript = """
set -e
CURRENT_SHELL=$(basename "$0")
IFS= read -r -d '' TARGET_SCRIPT
IFS= read -r -d '' INPUT_STRING
SCRIPT_DIR=$(mktemp -d)
trap 'rm -rf "$SCRIPT_DIR"' EXIT
SCRIPT_FILE="$SCRIPT_DIR/script.sh"
echo "$TARGET_SCRIPT" > "$SCRIPT_FILE"
chmod +x "$SCRIPT_FILE"
timeout "$1" "$CURRENT_SHELL" -c "JASPER_API='$2' '$SCRIPT_FILE'" << EOF
$INPUT_STRING
EOF
    """;

	@Timed("jasper.vm")
	public String runShellScript(String targetScript, String inputString, int timeoutMs) throws ScriptException, IOException, InterruptedException {
		var process = new ProcessBuilder(props.getShell(), "-c", wrapperScript, props.getShell(), String.valueOf(timeoutMs), api).start();
		try (var writer = new OutputStreamWriter(process.getOutputStream())) {
			writer.write(targetScript);
			writer.write("\0"); // null character as delimiter
			writer.write(inputString);
			writer.write("\0"); // both inputs must be null terminated
			writer.flush();
		} catch (IOException e) {
			logger.warn("Script terminated before receiving input.");
		}
		var finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
		if (!finished) {
			process.destroy();
			throw new RuntimeException("Script execution timed out");
		}
		var logs = getErrors(process.getErrorStream());
		var exitCode = process.exitValue();
		if (exitCode != 0) {
			logs = getErrors(process.getInputStream()) + logs;
			throw new ScriptException("Script execution failed with exit code: " + exitCode, logs);
		}
		try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			var output = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line).append("\n");
			}
			return output.toString();
		}
	}

	private String getErrors(InputStream is) throws IOException {
		var logs = new StringBuilder();
		try (var reader = new BufferedReader(new InputStreamReader(is))) {
			String line;
			while ((line = reader.readLine()) != null) {
				logger.debug(line);
				logs.append(line).append("\n");
			}
		}
		return logs.toString();
	}
}
