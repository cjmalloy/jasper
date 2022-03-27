package ca.hc.jasper.domain.proj;

import java.util.List;

public interface HasTags {
  List<String> getTags();
  default boolean local() {
	  return true;
  };
}
