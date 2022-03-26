package ca.hc.jasper.security.method;

import ca.hc.jasper.repository.RefRepository;
import ca.hc.jasper.repository.UserRepository;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.expression.method.*;
import org.springframework.security.core.Authentication;

public class CustomMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {

	private final UserRepository userRepository;
	private final RefRepository refRepository;

	public CustomMethodSecurityExpressionHandler(UserRepository userRepository, RefRepository refRepository) {
		this.userRepository = userRepository;
		this.refRepository = refRepository;
	}

	@Override
	protected MethodSecurityExpressionOperations createSecurityExpressionRoot(Authentication authentication, MethodInvocation invocation) {
		CustomMethodSecurityExpressionRoot root = new CustomMethodSecurityExpressionRoot(authentication, userRepository, refRepository);
		root.setThis(invocation.getThis());
		root.setTrustResolver(getTrustResolver());
		root.setRoleHierarchy(getRoleHierarchy());
		root.setDefaultRolePrefix(getDefaultRolePrefix());
		return root;
	}
}
