package ca.hc.jasper.repository.filter;

import static ca.hc.jasper.repository.spec.OriginSpec.isAnyOrigin;

import java.util.Arrays;
import java.util.List;

import ca.hc.jasper.domain.Origin;
import ca.hc.jasper.domain.proj.HasOrigin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

public class OriginList {
	public static final String REGEX = Origin.REGEX_NOT_BLANK + "([ +|]" + Origin.REGEX_NOT_BLANK + ")*";
	private static final Logger logger = LoggerFactory.getLogger(OriginList.class);

	private final List<String> origins;

	public OriginList(String query) {
		logger.debug(query);
		origins = Arrays.asList(query.split("[ +|]"));
	}

	public <T extends HasOrigin> Specification<T> spec() {
		return isAnyOrigin(origins);
	}

}
