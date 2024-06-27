package jasper.component.channel.delta;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.component.ConfigCache;
import jasper.component.IngestBundle;
import jasper.component.dto.Bundle;
import jasper.config.Props;
import jasper.domain.Ref;
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
import java.io.Writer;
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
		const [scriptContent, refString] = stdin.split('\\u0000');
		const patchedFs = {
		  ...fs,
		  readFileSync: (path, options) => {
			if (path === 0) return refString;
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
		const script = new vm.Script(scriptContent);
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
			var script = configs.getPluginConfig(scriptTag, ref.getOrigin(), Delta.class);
			if (isBlank(script.getJavascript())) continue;
			try {
				var output = runJavaScript(script.getJavascript(), ref, script.getTimeoutMs());
				var bundle = objectMapper.readValue(output, new TypeReference<Bundle>() {});
				ingest.createOrUpdate(bundle, ref.getOrigin());
			} catch (Exception e) {
				logger.error("Error running script", e);
				// TODO: Attach error message to Ref
			}
		}
	}

	private String runJavaScript(String targetScript, Ref ref, int timeoutMs) throws IOException, InterruptedException {
		var processBuilder = new ProcessBuilder(props.getNode(), "-e", nodeVmWrapperScript, "bun-arg-placeholder", ""+timeoutMs, api);
		var process = processBuilder.start();
		try (Writer writer = new OutputStreamWriter(process.getOutputStream())) {
			writer.write(targetScript);
			writer.write("\0"); // null character as delimiter
			writer.write(objectMapper.writeValueAsString(ref));
			writer.flush();
		} catch (IOException e) {
			logger.warn("Script terminated before receiving input.");
		}
		var finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
		if (!finished) {
			process.destroy();
			throw new RuntimeException("Script execution timed out");
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				logger.error(line);
			}
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			var exitCode = process.exitValue();
			if (exitCode != 0) {
				throw new RuntimeException("Script execution failed with exit code: " + exitCode);
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
