package jasper.domain.proj;

public interface HasOrigin extends HasModified {
	String getOrigin();
	default boolean local() {
		return getOrigin() == null || getOrigin().isEmpty();
	}
}
