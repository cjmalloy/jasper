package ca.hc.jasper.service.dto;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefDto {
	private String url;
	private String origin;
	private List<String> sources;
	private String title;
	private List<String> tags;
	private String comment;
	private List<String> alternateUrls;
	private Instant published;
	private Instant created;
	private Instant modified;
}
