package ca.hc.jasper.domain.proj;

public interface IsTag {
  String getTag();
  default boolean local() {
	  return true;
  };
}
