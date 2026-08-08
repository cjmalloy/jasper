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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static jasper.component.vm.RunProcess.runProcess;
import static java.lang.System.getProperty;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.setAttribute;
import static java.nio.file.Files.writeString;
import static java.security.MessageDigest.getInstance;
import static java.time.Instant.now;
import static org.apache.commons.codec.binary.Hex.encodeHexString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class JavaScript {
	private static final Logger logger = LoggerFactory.getLogger(JavaScript.class);
	private static final int INSTALL_TIMEOUT_MS = 300_000;

	@Autowired
	Props props;

	@Value("http://localhost:${server.port}")
	String api;

	private final ConcurrentHashMap<String, ReentrantLock> packageLocks = new ConcurrentHashMap<>();

	// language=JavaScript
	private final String nodeVmWrapperScript = """
		const fs = require('fs');
		const stdin = fs.readFileSync(0, 'utf-8');
		const timeout = parseInt(process.argv[1], 10) || 30_000;
		const api = process.argv[2];
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
			return require(mod);
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

	@Timed("jasper.vm")
	public String runJavaScript(String targetScript, String inputString, int timeoutMs) throws ScriptException, IOException {
		return runJavaScript(null, targetScript, inputString, timeoutMs);
	}

	@Timed("jasper.vm")
	public String runJavaScript(String packageJson, String targetScript, String inputString, int timeoutMs) throws ScriptException, IOException {
		var processBuilder = new ProcessBuilder(props.getNode(), "-e", nodeVmWrapperScript, ""+timeoutMs, api);
		if (isNotBlank(packageJson)) {
			processBuilder.directory(installPackages(packageJson).toFile());
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

	private Path installPackages(String packageJson) throws ScriptException, IOException {
		String packageHash;
		try {
			packageHash = encodeHexString(getInstance("SHA-256").digest(packageJson.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		var tmpDir = Objects.toString(getProperty("java.io.tmpdir"), "/tmp");
		var packageDir = Paths.get(tmpDir).resolve("jasper-node-" + packageHash).toAbsolutePath();
		var packageFile = packageDir.resolve("package.json");
		var installedFile = packageDir.resolve(".jasper-installed");

		var lock = packageLocks.computeIfAbsent(packageHash, key -> new ReentrantLock());
		lock.lock();
		try {
			createDirectories(packageDir);
			if (!exists(installedFile)) {
				writeString(packageFile, packageJson);
				var installProcess = new ProcessBuilder(
					props.getNpm(), "install", "--no-package-lock", "--no-audit", "--no-fund"
				).directory(packageDir.toFile()).start();
				runProcess(installProcess, INSTALL_TIMEOUT_MS);
				writeString(installedFile, "");
			}
			var now = FileTime.from(now());
			setAttribute(packageFile, "lastAccessTime", now);
			setAttribute(packageFile, "lastModifiedTime", now);
		} finally {
			lock.unlock();
		}
		return packageDir;
	}
}
