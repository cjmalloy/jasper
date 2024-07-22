package jasper.component.delta;

import jasper.component.ConfigCache;
import jasper.component.ScriptRunner;
import jasper.component.Tagger;
import jasper.domain.Ref;
import jasper.plugin.config.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static jasper.domain.proj.Tag.matchesTag;

@Profile("scripts")
@Component
public class DeltaScript implements Async.AsyncRunner {
	private static final Logger logger = LoggerFactory.getLogger(DeltaScript.class);

	@Autowired
	ConfigCache configs;

	@Autowired
	Async async;

	@Autowired
	ScriptRunner scriptRunner;

	@Autowired
	Tagger tagger;

	@PostConstruct
	void init() {
		async.addAsyncTag("plugin/delta", this);
		async.addAsyncTag("_plugin/delta", this);
	}

	@Override
	public String signature() {
		return "+plugin/delta";
	}

	@Override
	public void run(Ref ref) throws Exception {
		logger.info("{} Applying delta response to {} ({})", ref.getOrigin(), ref.getTitle(), ref.getUrl());
		var found = false;
		for (var scriptTag : ref.getTags().stream().filter(t -> matchesTag("plugin/delta", t) || matchesTag("_plugin/delta", t)).toList()) {
			var config = configs.getPluginConfig(scriptTag, ref.getOrigin(), Script.class);
			if (config.isPresent()) {
				scriptRunner.runScripts(ref, config.get());
				found = true;
			}
		}
		if (!found) tagger.attachError(ref.getOrigin(), ref, "Could not find delta script");
	}

}
