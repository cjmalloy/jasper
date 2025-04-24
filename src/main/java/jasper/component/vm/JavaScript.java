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
public class JavaScript {
	private static final Logger logger = LoggerFactory.getLogger(JavaScript.class);

	@Autowired
	Props props;

	@Value("http://localhost:${server.port}")
	String api;

	// language=JavaScript
	private final String nodeVmWrapperScript = """
		process.argv.splice(1, 1); // Workaround https://github.com/oven-sh/bun/issues/12209
		const fs = require('fs');
		const vm = require('node:vm');
		const stdin = fs.readFileSync(0, 'utf-8');
		const timeout = parseInt(process.argv[1], 10) || 30_000;
		const api = process.argv[2];
		const [targetScript, inputString] = stdin.split('\\u0000');
		const patchedFs = {
		  ...fs,
		  readFileSync: (path, options) => {
			if (path === 0) return inputString;
			return fs.readFileSync(path, options);
		  }
		};
		const context = vm.createContext({
	      console,
		  setTimeout,
		  process: {
		    env: { JASPER_API: api },
		    exit: process.exit,
		  },
		  require(mod) {
			if (mod === 'fs') return patchedFs;
			return require(mod);
		  }
		});
		const allowTopLevelAwait = 'const run = async () => {' + targetScript + '}; run().catch(err => {console.error(err);process.exit(1);});';
		const script = new vm.Script(allowTopLevelAwait);
		script.runInContext(context, {timeout});
	""";

	@Timed("jasper.vm")
	public String runJavaScript(String targetScript, String inputString, int timeoutMs) throws ScriptException, IOException, InterruptedException {
		var process = new ProcessBuilder(props.getNode(), "-e", nodeVmWrapperScript, "bun-arg-placeholder", ""+timeoutMs, api).start();
		try (var writer = new OutputStreamWriter(process.getOutputStream())) {
			writer.write(targetScript);
			writer.write("\0"); // null character as delimiter
			writer.write(inputString);
			writer.flush();
		} catch (IOException e) {
			logger.warn("Script terminated before receiving input.");
		}
		var finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
		var logs = getErrors(process.getErrorStream());
		if (!finished) {
			process.destroy();
			logs = getErrors(process.getInputStream()) + logs;
			throw new ScriptException("Script execution timed out", logs);
		}
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

	private String getErrors(InputStream is) {
		var logs = new StringBuilder();
		try (var reader = new BufferedReader(new InputStreamReader(is))) {
			String line;
			while ((line = reader.readLine()) != null) {
				logger.debug(line);
				logs.append(line).append("\n");
			}
		} catch (Exception ignored) { }
		return logs.toString();
	}
}
