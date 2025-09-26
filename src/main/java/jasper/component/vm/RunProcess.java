package jasper.component.vm;

import jasper.errors.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class RunProcess {
	private static final Logger logger = LoggerFactory.getLogger(RunProcess.class);

	public static String runProcess(Process process, int timeoutMs) throws ScriptException {
		final var output = new StringBuilder();
		final var errors = new StringBuilder();
		var outputThread = Thread.ofVirtual().start(() -> {
			try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append("\n");
				}
			} catch (IOException e) {
				logger.error("Error reading output stream: {}", e.getMessage());
			}
		});
		var errorThread = Thread.ofVirtual().start(() -> {
			try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					errors.append(line).append("\n");
				}
			} catch (IOException e) {
				logger.error("Error reading error stream: {}", e.getMessage());
			}
		});

		boolean finished;
		try {
			finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			process.destroy();
			Thread.currentThread().interrupt();
			throw new ScriptException("Script execution interrupted", ""+errors + output);
		}
		process.destroy();
		try {
			outputThread.join(100);
			errorThread.join(100);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (!finished) {
			throw new ScriptException("Script execution timed out", ""+errors + output);
		}
		var exitCode = process.exitValue();
		if (exitCode != 0) {
			throw new ScriptException("Script execution failed with exit code: " + exitCode, ""+errors + output);
		}
		return output.toString();
	}
}
