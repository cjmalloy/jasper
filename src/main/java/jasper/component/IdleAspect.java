package jasper.component;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Aspect that resets the idle timer when a REST controller method annotated
 * with @ClearIdle is invoked.
 */
@Aspect
@Component
public class IdleAspect {

	@Autowired
	IdleTracker idleTracker;

	/**
	 * Pointcut matching methods in classes annotated with @ClearIdle.
	 */
	@Pointcut("within(@jasper.component.ClearIdle *)")
	public void clearIdleClass() {}

	/**
	 * Pointcut matching methods annotated with @ClearIdle.
	 */
	@Pointcut("@annotation(jasper.component.ClearIdle)")
	public void clearIdleMethod() {}

	@Before("clearIdleClass() || clearIdleMethod()")
	public void resetIdleTimer() {
		idleTracker.clearIdle();
	}
}
