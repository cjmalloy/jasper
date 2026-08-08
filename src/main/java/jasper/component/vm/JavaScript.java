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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static jasper.component.vm.RunProcess.runProcess;
import static java.lang.System.getProperty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
		const dependencyBin = process.argv[3] === 'true'
		  ? process.env.PATH.split(path.delimiter).find(entry => entry.endsWith('node_modules/.bin'))
		  : null;
		const dependencyRequire = dependencyBin
		  ? createRequire(path.join(path.dirname(path.dirname(dependencyBin)), 'index.js'))
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
		var packages = requirements == null ? List.<String>of() : requirements.stream()
			.filter(Objects::nonNull)
			.filter(requirement -> isNotBlank(requirement))
			.toList();
		var command = new ArrayList<String>();
		if (!packages.isEmpty()) {
			command.addAll(List.of(props.getNpx(), "--yes"));
			packages.forEach(requirement -> command.addAll(List.of("--package", requirement)));
			command.add("--");
		}
		command.addAll(List.of(props.getNode(), "-e", nodeVmWrapperScript, ""+timeoutMs, api, Boolean.toString(!packages.isEmpty())));
		var processBuilder = new ProcessBuilder(command);
		if (!packages.isEmpty()) {
			processBuilder.environment().put(
				"npm_config_cache",
				Paths.get(Objects.toString(getProperty("java.io.tmpdir"), "/tmp"), "jasper-npx").toString()
			);
		}
		var scriptProcess = processBuilder.start();
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
