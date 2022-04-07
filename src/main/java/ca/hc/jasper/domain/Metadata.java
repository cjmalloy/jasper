package ca.hc.jasper.domain;

import java.util.List;

import lombok.*;

@Getter
@Setter
@Builder
public class Metadata {
	private List<String> responses;
	private List<String> comments;

	// TODO: remote stats
//	private List<String> origins;
//	private List<String> sourcesAcrossOrigins;
//	private List<String> responsesAcrossOrigins;
//	private List<String> commentsAcrossOrigins;

	// TODO: recursive stats
//	private Long recursiveCommentCount;
//	private Long recursiveResponseCount;
//	private Instant lastRecurse;
}
