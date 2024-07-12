package jasper.component.cron;

import jasper.component.ConfigCache;
import jasper.component.ScriptRunner;
import jasper.domain.Ref;
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

	@PostConstruct
	void init() {
		cron.addCronTag("plugin/script", this);
		cron.addCronTag("+plugin/script", this);
		cron.addCronTag("_plugin/script", this);
	}

	@Override
	public void run(Ref ref) throws Exception {
		logger.debug("{} Applying delta response to {} ({})", ref.getOrigin(), ref.getTitle(), ref.getUrl());
		for (var scriptTag : ref.getTags().stream().filter(t -> matchesTag("plugin/script", publicTag(t))).toList()) {
			configs
				.getPluginConfig(scriptTag, ref.getOrigin(), Script.class)
				.ifPresent(c -> scriptRunner.runScripts(ref, c));
		}
	}

}
