package jasper.component;

import jasper.component.script.ScriptDefaults;
import jasper.domain.Ref;
import jasper.plugin.config.Script;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScriptRunnerTest {

	@Test
	void resolvesScriptTagConfigurationToInstalledParent() throws Exception {
		var configs = mock(ConfigCache.class);
		var scriptDefaults = mock(ScriptDefaults.class);
		var runner = new ScriptRunner();
		runner.configs = configs;
		runner.scriptDefaults = scriptDefaults;
		var ref = new Ref();
		var config = Script.builder().build();
		when(configs.getPluginConfig("_plugin/delta/scrape/ref", "", Script.class)).thenReturn(Optional.empty());
		when(configs.getPluginConfig("_plugin/delta/scrape", "", Script.class)).thenReturn(Optional.of(config));

		runner.runScripts(ref, "_plugin/delta/scrape/ref");

		verify(scriptDefaults).runScript(ref, "_plugin/delta/scrape");
	}
}
