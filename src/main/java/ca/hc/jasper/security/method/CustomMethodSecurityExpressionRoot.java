package ca.hc.jasper.security.method;

import java.util.List;

import ca.hc.jasper.component.UserManager;
import ca.hc.jasper.domain.*;
import ca.hc.jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

public class CustomMethodSecurityExpressionRoot extends SecurityExpressionRoot implements MethodSecurityExpressionOperations {
	private final Logger logger = LoggerFactory.getLogger(CustomMethodSecurityExpressionRoot.class);

	static final List<String> TAGGABLE = List.of("TAG", "USER", "QUEUE");

	private Object filterObject;

	private Object returnObject;

	private Object target;

	private final UserManager userManager;

	private final RefRepository refRepository;

	CustomMethodSecurityExpressionRoot(Authentication a, UserManager userManager, RefRepository refRepository) {
		super(a);
		this.userManager = userManager;
		this.refRepository = refRepository;
	}

	@Override
	public boolean hasPermission(Object target, Object permission) {
		if (target == null) return true; // Makes deletes idempotent
		if (hasRole("ADMIN")) return true;
		String targetType = target.getClass().getSimpleName().toUpperCase();
		if (targetType.equals("REF")) {
			if (permission == create) return hasRole("USER");
			if (permission == read) return canReadRef((Ref) target);
			if (permission == write) return canWriteRef((Ref) target);
			if (permission == delete) return canWriteRef((Ref) target);
			return false;
		}
		if (targetType.equals("TAG")) {
			return hasPermission(((Tag) target).getTag(), permission);
		}
		if (targetType.equals("USER")) {
			return hasPermission(((User) target).getTag(), permission);
		}
		if (targetType.equals("QUEUE")) {
			return hasPermission(((Queue) target).getTag(), permission);
		}
		logger.error("No permissions for {}", targetType);
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean hasPermission(Object targetId, String targetType, Object permission) {
		if (targetId == null) return false;
		if (hasRole("ADMIN")) return true;
		targetType = targetType.toUpperCase();
		if (targetType.equals("REF")) {
			if (permission == create) return hasRole("USER");
			String url = (String) targetId;
			Ref target = refRepository.findOneByUrlAndOrigin(url, "").orElse(null);
			return hasPermission(target, permission);
		}
		if (TAGGABLE.contains(targetType)) {
			if (hasRole("MOD")) return true;
			String tag = (String) targetId;
			if (permission == create) return false;
			if (permission == read) return canReadTag(tag);
			if (permission == write) return canWriteTag(tag);
			if (permission == delete) return false;
			return false;
		}
		logger.error("No permissions for {}", targetType);
		throw new UnsupportedOperationException();
	}

	public UserDetails getPrincipal() {
		return (UserDetails) super.getPrincipal();
	}

	public String getUserTag() {
		return getPrincipal().getUsername();
	}

	public List<String> getReadAccess() {
		return userManager.getReadAccess(getUserTag());
	}

	public List<String> getWriteAccess() {
		return userManager.getReadAccess(getUserTag());
	}

	public boolean canReadRef(Ref ref) {
		if (ref.getTags() != null) {
			if (ref.getTags().contains("public")) return true;
			if (hasRole("USER")) {
				if (ref.getTags().contains(getUserTag())) return true;
				return anyMatch(getReadAccess(), ref.getTags());
			}
		}
		return false;
	}

	public boolean canWriteRef(Ref ref) {
		if (ref.getTags() != null && hasRole("USER")) {
			if (ref.getTags().contains(getUserTag())) return true;
			return anyMatch(getWriteAccess(), ref.getTags());
		}
		return false;
	}

	public boolean canReadTag(String tag) {
		if (!tag.startsWith("_")) return true;
		if (hasRole("USER")) {
			var readAccess = getReadAccess();
			if (readAccess == null) return false;
			return readAccess.contains(tag);
		}
		return false;
	}

	public boolean canWriteTag(String tag) {
		if (hasRole("USER")) {
			var writeAccess = getWriteAccess();
			if (writeAccess == null) return false;
			return writeAccess.contains(tag);
		}
		return false;
	}

	private static boolean anyMatch(List<String> permitted, List<String> tags) {
		if (permitted == null) return false;
		if (permitted.isEmpty()) return false;
		if (tags == null) return false;
		if (tags.isEmpty()) return false;
		for (String p : permitted) {
			for (String t : tags) {
				if (p.equals(t)) return true;
			}
		}
		return false;
	}

	@Override
	public void setFilterObject(Object filterObject) {
		this.filterObject = filterObject;
	}

	@Override
	public Object getFilterObject() {
		return this.filterObject;
	}

	@Override
	public void setReturnObject(Object returnObject) {
		this.returnObject = returnObject;
	}

	@Override
	public Object getReturnObject() {
		return this.returnObject;
	}

	/**
	 * Sets the "this" property for use in expressions. Typically this will be the "this"
	 * property of the {@code JoinPoint} representing the method invocation which is being
	 * protected.
	 * @param target the target object on which the method in is being invoked.
	 */
	void setThis(Object target) {
		this.target = target;
	}

	@Override
	public Object getThis() {
		return this.target;
	}

}
