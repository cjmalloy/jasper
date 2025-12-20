package jasper.service.dto;

import java.io.Serializable;

public record SmtpWebhookDto(
	String spf,
	String[] references,
	String id,
	String date,
	String subject,
	String resentDate,
	String resentId,
	Body body,
	Addresses addresses,
	EmailAttachment[] attachments,
	EmailEmbeddedFile[] embeddedFiles
) implements Serializable {

	public record Body(
		String text,
		String html
	) implements Serializable {}

	public record Addresses(
		EmailAddress from,
		EmailAddress to,
		EmailAddress[] replyTo,
		EmailAddress[] cc,
		EmailAddress[] bcc,
		String[] inReplyTo,
		String html,
		EmailAddress resentFrom,
		EmailAddress[] resentTo,
		EmailAddress[] resentCc,
		EmailAddress[] resentBcc
	) implements Serializable {}

	public record EmailAddress(
		String name,
		String address
	) implements Serializable {}

	public record EmailAttachment(
		String filename,
		String contentType,
		String data
	) implements Serializable {}

	public record EmailEmbeddedFile(
		String cid,
		String contentType,
		String data
	) implements Serializable {}
}

