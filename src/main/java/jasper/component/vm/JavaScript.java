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

import static jasper.component.vm.RunProcess.runProcess;

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
		const module = require('module');
		const path = require('path');
		const vm = require('node:vm');
		const builtins = new Set((module.builtinModules || []).flatMap((mod) => [mod, 'node:' + mod]));
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
		const moduleCache = new Map();
		let context;
		const sandboxRequire = (mod, parentDir = process.cwd()) => {
			if (mod === 'fs' || mod === 'node:fs') return patchedFs;
			const resolved = require.resolve(mod, { paths: [parentDir] });
			if (builtins.has(mod)) return require(mod);
			if (moduleCache.has(resolved)) return moduleCache.get(resolved).exports;
			if (resolved.endsWith('.json')) {
			  const jsonModule = { exports: JSON.parse(fs.readFileSync(resolved, 'utf-8')) };
			  moduleCache.set(resolved, jsonModule);
			  return jsonModule.exports;
			}
			const module = { exports: {} };
			moduleCache.set(resolved, module);
			const dirname = path.dirname(resolved);
			const localRequire = (childMod) => sandboxRequire(childMod, dirname);
			localRequire.resolve = (childMod) => require.resolve(childMod, { paths: [dirname] });
			const source = fs.readFileSync(resolved, 'utf-8');
			context.__jasperModule = module;
			context.__jasperLocalRequire = localRequire;
			context.__jasperFilename = resolved;
			context.__jasperDirname = dirname;
			const wrapper = '(function (exports, require, module, __filename, __dirname) {\\n' + source + '\\n})(__jasperModule.exports, __jasperLocalRequire, __jasperModule, __jasperFilename, __jasperDirname);';
			try {
			  new vm.Script(wrapper, { filename: resolved }).runInContext(context, { timeout });
			  return module.exports;
			} catch (err) {
			  moduleCache.delete(resolved);
			  if (err && err.name === 'SyntaxError') {
			    // ESM-only module: fall back to the host loader for native ESM/CJS interop
			    const hostModule = { exports: require(resolved) };
			    moduleCache.set(resolved, hostModule);
			    return hostModule.exports;
			  }
			  throw err;
			} finally {
			  delete context.__jasperModule;
			  delete context.__jasperLocalRequire;
			  delete context.__jasperFilename;
			  delete context.__jasperDirname;
			}
		};
		context = vm.createContext({
	      console,
		  setTimeout,
		  clearTimeout,
		  setInterval,
		  clearInterval,
		  queueMicrotask,
		  Buffer,
		  URL,
		  URLSearchParams,
		  TextEncoder,
		  TextDecoder,
		  fetch,
		  process: {
		    env: { JASPER_API: api },
		    exit: process.exit,
		  },
		  require: (mod) => sandboxRequire(mod),
		});
		const allowTopLevelAwait = 'const run = async () => {' + targetScript + '}; run().catch(err => {console.error(err);process.exit(1);});';
		const script = new vm.Script(allowTopLevelAwait);
		script.runInContext(context, {timeout});
	""";

	@Timed("jasper.vm")
	public String runJavaScript(String targetScript, String inputString, int timeoutMs) throws ScriptException, IOException {
		var process = new ProcessBuilder(props.getNode(), "-e", nodeVmWrapperScript, ""+timeoutMs, api).start();
		try (var writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
			writer.write(targetScript);
			writer.write("\0"); // null character as delimiter
			writer.write(inputString);
			writer.flush();
		} catch (IOException e) {
			logger.warn("Script terminated before receiving input.");
		}
		return runProcess(process, timeoutMs);
	}
}
