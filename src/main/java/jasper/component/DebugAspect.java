package jasper.component;

import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static jasper.domain.proj.HasTags.hasMatchingTag;

/**
 * Aspect that logs every Ingest write to a Ref tagged +plugin/debug
 * (in either the incoming or the stored Ref), including the caller stack trace.
 */
@Aspect
@Component
public class DebugAspect {
	private static final Logger logger = LoggerFactory.getLogger(DebugAspect.class);

	@Autowired
	RefRepository refRepository;

	@Before("execution(public void jasper.component.Ingest.*(String, jasper.domain.Ref, ..)) && args(rootOrigin, ref, ..)")
	public void debugWrite(JoinPoint joinPoint, String rootOrigin, Ref ref) {
		var existing = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin()).orElse(null);
		if (!hasMatchingTag(ref, "+plugin/debug") && !hasMatchingTag(existing, "+plugin/debug")) return;
		logger.info("{} +plugin/debug Ingest {} {} {}: tags {} plugins {}",
			rootOrigin, joinPoint.getSignature().getName(), ref.getOrigin(), ref.getUrl(), ref.getTags(), ref.getPlugins(),
			new Throwable("+plugin/debug stack trace"));
	}

	@Before("execution(public void jasper.component.Ingest.delete(String, String, String)) && args(rootOrigin, url, origin)")
	public void debugDelete(JoinPoint joinPoint, String rootOrigin, String url, String origin) {
		var existing = refRepository.findOneByUrlAndOrigin(url, origin).orElse(null);
		if (!hasMatchingTag(existing, "+plugin/debug")) return;
		logger.info("{} +plugin/debug Ingest {} {} {}: tags {} plugins {}",
			rootOrigin, joinPoint.getSignature().getName(), origin, url, existing.getTags(), existing.getPlugins(),
			new Throwable("+plugin/debug stack trace"));
	}
}
