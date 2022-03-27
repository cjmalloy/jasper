package ca.hc.jasper.service.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueueDto {
	private String tag;
	private String origin;
	private String name;
	private String comment;
	private String bounty;
	private Duration maxAge;
	private List<String> approvers;
	private Instant modified;
}
