package jasper.component.cron;

import jakarta.annotation.PostConstruct;
import jasper.component.ConfigCache;
import jasper.component.ScriptRunner;
import jasper.component.Tagger;
import jasper.domain.Ref;
import jasper.errors.UntrustedScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static jasper.domain.proj.Tag.matchesTag;
import static jasper.domain.proj.Tag.publicTag;

@Profile("scripts")
@Component
public class Script implements Scheduler.CronRunner {
	private static final Logger logger = LoggerFactory.getLogger(Script.class);

	@Autowired
	ConfigCache configs;

	@Autowired
	Scheduler cron;

	@Autowired
	ScriptRunner scriptRunner;

	@Autowired
	Tagger tagger;

	@PostConstruct
	void init() {
		// TODO: redo on template change
		cron.addCronTag("plugin/script", this);
		cron.addCronTag("+plugin/script", this);
		cron.addCronTag("_plugin/script", this);
	}

	@Override
	public void run(Ref ref) throws Exception {
		var found = false;
		logger.debug("{} Searching scripts for {} ({})", ref.getOrigin(), ref.getTitle(), ref.getUrl());
		for (var scriptTag : ref.getTags().stream().filter(t -> matchesTag("plugin/script", publicTag(t))).toList()) {
			var config = configs.getPluginConfig(scriptTag, ref.getOrigin(), jasper.plugin.config.Script.class);
			if (config.isPresent()) {
				try {
					logger.info("{} Running script {} to {} ({})", scriptTag, ref.getOrigin(), ref.getTitle(), ref.getUrl());
					scriptRunner.runScripts(ref, scriptTag, config.get());
				} catch (UntrustedScriptException e) {
					logger.error("{} Script hash not whitelisted: {}", ref.getOrigin(), e.getScriptHash());
					tagger.attachError(ref.getOrigin(), ref, "Script hash not whitelisted", e.getScriptHash());
				}
				found = true;
			}
		}
		if (!found) tagger.attachError(ref.getOrigin(), ref, "Could not find script");
	}

}
