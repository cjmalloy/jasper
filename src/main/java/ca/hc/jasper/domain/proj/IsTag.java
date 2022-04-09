package ca.hc.jasper.domain.proj;

public interface IsTag extends HasOrigin {
	String getTag();
	String getName();

	default String getQualifiedTag() {
		return getTag() + getOrigin();
	}
}
