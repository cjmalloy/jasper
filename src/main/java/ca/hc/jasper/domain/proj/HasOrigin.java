package ca.hc.jasper.domain.proj;

public interface HasOrigin {
  String getOrigin();
  default boolean local() {
	  return getOrigin() == null || getOrigin().isEmpty();
  }
}
