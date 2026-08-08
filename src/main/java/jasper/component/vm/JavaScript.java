package jasper.component.vm;

import io.micrometer.core.annotation.Timed;
import jasper.config.Props;
import jasper.errors.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static jasper.component.vm.RunProcess.runProcess;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@Component
public class JavaScript {
	private static final Logger logger = LoggerFactory.getLogger(JavaScript.class);

	@Autowired
	Props props;

	@Value("http://localhost:${server.port}")
	String api;

	// language=JavaScript
	private final String nodeVmWrapperScript = """
		const fs = require('fs');
		const path = require('path');
		const { createRequire } = require('module');
		const stdin = fs.readFileSync(0, 'utf-8');
		const timeout = parseInt(process.argv[1], 10) || 30_000;
		const api = process.argv[2];
		const dependencyBin = process.env.PATH.split(path.delimiter).find(entry => entry.endsWith('node_modules/.bin'));
		const dependencyRequire = dependencyBin
		  ? createRequire(path.join(path.dirname(path.dirname(dependencyBin)), 'package.json'))
		  : require;
		const [targetScript, inputString] = (i => i < 0 ? [stdin, ''] : [stdin.slice(0, i), stdin.slice(i + 1)])(stdin.indexOf('\\u0000'));
		const patchedFs = {
		  ...fs,
		  readFileSync: (path, options) => {
			if (path === 0) return inputString;
			return fs.readFileSync(path, options);
		  }
		};
		const patchedRequire = (mod) => {
			if (mod === 'fs') return patchedFs;
			return dependencyRequire(mod);
		};
		const scriptProcess = {
		  env: { JASPER_API: api },
		  exit: (code) => process.exit(code),
		};
		const AsyncFunction = Object.getPrototypeOf(async function(){}).constructor;
		const script = new AsyncFunction('require', 'console', 'setTimeout', 'process', targetScript);
		script(patchedRequire, console, setTimeout, scriptProcess).catch(err => {
		  console.error(err);
		  process.exit(1);
		});
	""";

	public String runJavaScript(String targetScript, String inputString, int timeoutMs) throws ScriptException, IOException {
		return runJavaScript(null, targetScript, inputString, timeoutMs);
	}

	@Timed("jasper.vm")
	public String runJavaScript(List<String> requirements, String targetScript, String inputString, int timeoutMs) throws ScriptException, IOException {
		var command = new ArrayList<String>();
		if (isNotEmpty(requirements)) {
			command.addAll(List.of(props.getNpx(), "--yes"));
			requirements.forEach(requirement -> command.addAll(List.of("--package", requirement)));
			command.add("--");
		}
		command.addAll(List.of(props.getNode(), "-e", nodeVmWrapperScript, ""+timeoutMs, api));
		var scriptProcess = new ProcessBuilder(command).start();
		try (var writer = new OutputStreamWriter(scriptProcess.getOutputStream(), StandardCharsets.UTF_8)) {
			writer.write(targetScript);
			writer.write("\0"); // null character as delimiter
			writer.write(inputString);
			writer.flush();
		} catch (IOException e) {
			logger.warn("Script terminated before receiving input.");
		}
		return runProcess(scriptProcess, timeoutMs);
	}
}
