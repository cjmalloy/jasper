package ca.hc.jasper.security.method;

import ca.hc.jasper.component.UserManager;
import ca.hc.jasper.repository.RefRepository;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;

public class CustomMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {

	private final UserManager userManager;
	private final RefRepository refRepository;

	public CustomMethodSecurityExpressionHandler(UserManager userManager, RefRepository refRepository) {
		this.userManager = userManager;
		this.refRepository = refRepository;
	}

	@Override
	protected MethodSecurityExpressionOperations createSecurityExpressionRoot(Authentication authentication, MethodInvocation invocation) {
		CustomMethodSecurityExpressionRoot root = new CustomMethodSecurityExpressionRoot(authentication, userManager, refRepository);
		root.setThis(invocation.getThis());
		root.setTrustResolver(getTrustResolver());
		root.setRoleHierarchy(getRoleHierarchy());
		root.setDefaultRolePrefix(getDefaultRolePrefix());
		return root;
	}
}
