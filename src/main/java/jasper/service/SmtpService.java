package jasper.service;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.SmtpWebhookDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SmtpService {
	private static final Logger logger = LoggerFactory.getLogger(SmtpService.class);

	@Autowired
	DtoMapper mapper;

	@Autowired
	RefService refService;

	@Autowired
	RefRepository refRepository;

	@Timed(value = "jasper.service", extraTags = {"service", "smtp"}, histogram = true)
	public void create(SmtpWebhookDto email) {
		var ref = mapper.smtpToDomain(email);
		Page<Ref> source = refRepository.findAll(
				RefFilter.builder()
						.query("plugin/email:!internal")
						.endsTitle(ref.getTitle())
						.publishedBefore(ref.getPublished()).build().spec(),
				PageRequest.of(0, 1, Sort.by(Sort.Order.desc(Ref_.PUBLISHED))));
		if (!source.isEmpty()) {
			ref.setSources(List.of(source.getContent().get(0).getUrl()));
			ref.getTags().add("internal");
		}
		refService.create(ref);
	}

}
