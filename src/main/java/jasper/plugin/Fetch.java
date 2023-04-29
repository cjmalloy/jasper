package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import jasper.repository.filter.RefFilter;
import jasper.repository.filter.TagFilter;
import jasper.repository.filter.TemplateFilter;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Fetch {
	private String search;
	private String query;
	private int size;
	private int page;
	private String type;
	private String replyUrl;

	public RefFilter getRefFilter() {
		return RefFilter.builder()
			.query(query)
			.search(search)
			.build();
	}

	public TemplateFilter getTemplateFilter() {
		return TemplateFilter.builder()
			.query(query)
			.search(search)
			.build();
	}

	public TagFilter getFilter() {
		return TagFilter.builder()
			.query(query)
			.search(search)
			.build();
	}
}
