package jasper.component;

import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Aspect that traces every Ingest write to a Ref tagged +plugin/debug,
 * including the caller stack trace.
 */
@Aspect
@Component
public class DebugAspect {

	@Lazy
	@Autowired
	Tagger tagger;

	@Autowired
	RefRepository refRepository;

	@Before("execution(public * jasper.component.Ingest.*(..))")
	public void debugWrite(JoinPoint joinPoint) {
		for (var arg : joinPoint.getArgs()) {
			if (arg instanceof Ref ref) tagger.debug(ref, "Ingest " + joinPoint.getSignature().getName());
		}
	}

	@Before("execution(public void jasper.component.Ingest.delete(String, String, String)) && args(rootOrigin, url, origin)")
	public void debugDelete(String rootOrigin, String url, String origin) {
		refRepository.findOneByUrlAndOrigin(url, origin).ifPresent(ref -> tagger.debug(ref, "Ingest delete"));
	}
}
