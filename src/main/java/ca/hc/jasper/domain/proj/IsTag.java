package ca.hc.jasper.domain.proj;

public interface IsTag extends HasOrigin {

	String getTag();

	default String getQualifiedTag() {
		return getTag() + getOrigin();
	}
}
