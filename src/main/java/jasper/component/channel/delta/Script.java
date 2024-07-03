package jasper.component.channel.delta;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.component.ConfigCache;
import jasper.component.IngestBundle;
import jasper.component.dto.Bundle;
import jasper.component.dto.ComponentDtoMapper;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.errors.ScriptException;
import jasper.plugin.config.Delta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.TimeUnit;

import static jasper.domain.proj.Tag.matchesTag;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Profile("scripts")
@Component
public class Script implements Async.AsyncRunner {
	private static final Logger logger = LoggerFactory.getLogger(Script.class);

	@Autowired
	Props props;

	@Autowired
	Async async;

	@Autowired
	IngestBundle ingest;

	@Autowired
	ConfigCache configs;

	@Autowired
	ComponentDtoMapper mapper;

	@Autowired
	ObjectMapper objectMapper;

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

	@PostConstruct
	void init() {
		async.addAsyncTag("plugin/delta", this);
	}

	@Override
	public String signature() {
		return "+plugin/delta";
	}

	@Override
	public void run(Ref ref) throws Exception {
		logger.debug("Applying delta response to {} ({})", ref.getTitle(), ref.getUrl());
		for (var scriptTag : ref.getTags().stream().filter(t -> matchesTag("plugin/delta", t)).toList()) {
			var delta = configs.getPluginConfig(scriptTag, ref.getOrigin(), Delta.class);
			if (isBlank(delta.getScript())) continue;
			if (!"javascript".equals(delta.getLanguage())) {
				// Only Javascript is supported right now
				logger.error("Script runtime not supported {}", delta.getLanguage());
				ingest.attachError(ref, "Script runtime not supported: " + delta.getLanguage(), ref.getOrigin());
				return;
			}
			String output;
			try {
				output = runJavaScript(delta.getScript(), objectMapper.writeValueAsString(mapper.domainToDto(ref)), delta.getTimeoutMs());
			} catch (ScriptException e) {
				logger.error("Error running script", e);
				ingest.attachError(ref, e.getMessage(), e.getLogs(), ref.getOrigin());
				return;
			} catch (Exception e) {
				logger.error("Error running script", e);
				ingest.attachError(ref, e.getMessage(), ref.getOrigin());
				return;
			}
			try {
				var bundle = objectMapper.readValue(output, new TypeReference<Bundle>() {});
				ingest.createOrUpdate(bundle, ref.getOrigin());
			} catch (Exception e) {
				logger.error("Error parsing script return value", e);
				ingest.attachError(ref, "Error parsing script output", output, ref.getOrigin());
			}
		}
	}

	String runJavaScript(String targetScript, String inputString, int timeoutMs) throws IOException, InterruptedException, ScriptException {
		var processBuilder = new ProcessBuilder(props.getNode(), "-e", nodeVmWrapperScript, "bun-arg-placeholder", ""+timeoutMs, api);
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
