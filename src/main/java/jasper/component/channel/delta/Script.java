package jasper.component.channel.delta;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.component.ConfigCache;
import jasper.component.Ingest;
import jasper.component.IngestExt;
import jasper.config.Props;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.errors.NotFoundException;
import jasper.plugin.config.Delta;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
	Ingest ingestRef;

	@Autowired
	IngestExt ingestExt;

	@Autowired
	ConfigCache configs;

	@Autowired
	ObjectMapper objectMapper;

	// language=JavaScript
	private final String nodeVmWrapperScript = """
		const fs = require('fs');
		const vm = require('node:vm');
		const stdin = fs.readFileSync(0, 'utf-8');
		const timeout = parseInt(process.argv[2], 10) || 30_000;
		const [scriptContent, refString] = stdin.split('\\u0000');
		const patchedFs = {
		  ...fs,
		  readFileSync: (path, options) => {
			if (path === 0) return refString;
			return fs.readFileSync(path, options);
		  }
		};
		const patchedConsole = {
		  ...console,
		  log: (...args) => console.log(...args),
		  error: (...args) => console.error(...args),
		};
		const context = vm.createContext({
	      console: patchedConsole,
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
				var entitiesOut = objectMapper.readValue(output, new TypeReference<ScriptOutput>() {});
				if (entitiesOut.ref != null) for (var refOut : entitiesOut.ref) {
					refOut.setOrigin(ref.getOrigin());
					try {
						ingestRef.update(refOut, false);
						logger.debug("Script Updated Ref ({})", refOut.getUrl());
					} catch (NotFoundException e) {
						ingestRef.create(refOut, false);
						logger.debug("Script Created Ref ({})", refOut.getUrl());
					}
				}
				if (entitiesOut.ext != null) for (var extOut : entitiesOut.ext) {
					extOut.setOrigin(ref.getOrigin());
					try {
						ingestExt.update(extOut, false);
						logger.debug("Script Updated Ext ({})", extOut.getTag());
					} catch (NotFoundException e) {
						ingestExt.create(extOut, false);
						logger.debug("Script Created Ext ({})", extOut.getTag());
					}
				}
				// TODO: Plugins, Templates, and Users
			} catch (Exception e) {
				logger.error("Error running script", e);
				// TODO: Attach error message to Ref
			}
		}
	}

	private String runJavaScript(String targetScript, Ref ref, int timeoutMs) throws IOException, InterruptedException {
		var processBuilder = new ProcessBuilder(props.getNode(), "-e", nodeVmWrapperScript, String.valueOf(timeoutMs));
		processBuilder.redirectErrorStream(true);
		var process = processBuilder.start();

		try (Writer writer = new OutputStreamWriter(process.getOutputStream())) {
			writer.write(targetScript);
			writer.write("\0"); // null character as delimiter
			writer.write(objectMapper.writeValueAsString(ref));
			writer.flush();
		}

		var finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
		if (!finished) {
			process.destroy();
			throw new RuntimeException("Script execution timed out");
		}

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			var output = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line).append("\n");
			}
			var exitCode = process.exitValue();
			if (exitCode != 0) {
				throw new RuntimeException("Script execution failed with exit code: " + exitCode);
			}
			return output.toString();
		}
	}

	@Getter
	@Setter
	private static class ScriptOutput {
		private Ref[] ref;
		private Ext[] ext;
		private Plugin[] plugin;
		private Template[] template;
		private User[] user;
	}

}
