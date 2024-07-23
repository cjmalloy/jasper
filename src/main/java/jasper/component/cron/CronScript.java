package jasper.component.cron;

import jasper.component.ConfigCache;
import jasper.component.ScriptRunner;
import jasper.component.Tagger;
import jasper.domain.Ref;
import jasper.errors.UntrustedScriptException;
import jasper.plugin.config.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static jasper.domain.proj.Tag.matchesTag;
import static jasper.domain.proj.Tag.publicTag;

@Profile("scripts")
@Component
public class CronScript implements Scheduler.CronRunner {
	private static final Logger logger = LoggerFactory.getLogger(CronScript.class);

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
		cron.addCronTag("plugin/script", this);
		cron.addCronTag("+plugin/script", this);
		cron.addCronTag("_plugin/script", this);
	}

	@Override
	public void run(Ref ref) throws Exception {
		logger.info("{} Applying delta response to {} ({})", ref.getOrigin(), ref.getTitle(), ref.getUrl());
		var found = false;
		for (var scriptTag : ref.getTags().stream().filter(t -> matchesTag("plugin/script", publicTag(t))).toList()) {
			var config = configs.getPluginConfig(scriptTag, ref.getOrigin(), Script.class);
			if (config.isPresent()) {
				try {
					scriptRunner.runScripts(ref, config.get());
				} catch (UntrustedScriptException e) {
					logger.error("{} Script hash not whitelisted: {}", ref.getOrigin(), e.getScriptHash());
					tagger.attachError(ref.getOrigin(), ref, "Script hash not whitelisted", e.getScriptHash());
				}
				found = true;
			}
		}
		if (!found) tagger.attachError(ref.getOrigin(), ref, "Could not find cron script");
	}

}
