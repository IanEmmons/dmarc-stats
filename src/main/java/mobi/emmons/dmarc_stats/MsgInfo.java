package mobi.emmons.dmarc_stats;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.angus.mail.util.BASE64DecoderStream;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.ParseException;

public record MsgInfo(int imapMsgNum, String from, String subject, Instant time,
	String xmlPart) {

	private static final ContentType CT_GZIP = newContentType("application/gzip");
	private static final ContentType CT_OCTET_STREAM = newContentType("application/octet-stream");
	private static final ContentType CT_ZIP = newContentType("application/zip");

	public MsgInfo(Message message) throws MessagingException, IOException {
		this(message.getMessageNumber(),
			getFrom(message),
			message.getSubject(),
			message.getSentDate().toInstant(),
			getXmlPart(message));
	}

	private static ContentType newContentType(String type) {
		try {
			return new ContentType(type);
		} catch (ParseException ex) {
			throw new UncheckedParseException(ex);
		}
	}

	private static String getFrom(Message message) throws MessagingException {
		return Arrays.stream(message.getFrom())
			.filter(Objects::nonNull)
			.map(Object::toString)
			.collect(Collectors.joining(", "));
	}


	private static String getXmlPart(Message message) throws MessagingException, IOException {
		List<String> xmlParts = new ArrayList<>();
		getXmlParts(message.getContent(), getContentType(message), getFileExt(message), xmlParts);
		if (xmlParts.size() < 1) {
			throw new UnexpectedMessageFormatException(
				"No XML file found in DMARC email zip attachment");
		} else if (xmlParts.size() > 1) {
			throw new UnexpectedMessageFormatException(
				"Found more than one XML file in DMARC email zip attachment (%1$d)",
				xmlParts.size());
		}
		return xmlParts.getFirst();
	}

	private static void getXmlParts(Object content, ContentType contentType, String fileExt, List<String> xmlParts)
			throws MessagingException, IOException {
		if (content instanceof MimeMultipart multiPart) {
			for (int i = 0; i < multiPart.getCount(); ++i) {
				BodyPart part = multiPart.getBodyPart(i);
				getXmlParts(part.getContent(), getContentType(part), getFileExt(part), xmlParts);
			}
		} else if (content instanceof BASE64DecoderStream b64Stream) {
			if (CT_ZIP.match(contentType) || "zip".equals(fileExt)) {
				decodeZipAttachment(b64Stream, xmlParts);
			} else if ((CT_GZIP.match(contentType) || CT_OCTET_STREAM.match(contentType)) && "gz".equals(fileExt)) {
				xmlParts.add(decodeGZipAttachment(b64Stream));
			} else {
				throw new UnexpectedMessageFormatException(
					"Message content has content type %1$s and file extension %2$s",
					contentType.getBaseType(), fileExt);
			}
		} else if (content instanceof String) {
			// Do nothing: We are ignoring message body parts
		} else {
			throw new UnexpectedMessageFormatException(
				"Message content has type %1$s", content.getClass());
		}
	}

	private static void decodeZipAttachment(BASE64DecoderStream b64Stream, List<String> xmlParts) throws IOException {
		try (
			InputStream is = b64Stream;
			ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8);
		) {
			for (;;) {
				var zipEntry = zis.getNextEntry();
				if (zipEntry == null) {
					break;
				}
				String zipEntryExt = getFileExt(zipEntry);
				if ("xml".equals(zipEntryExt)) {
					xmlParts.add(inputStreamToString(zis));
				} else {
					throw new UnexpectedMessageFormatException(
						"Message content has file extension %1$s", zipEntryExt);
				}
				zis.closeEntry();
			}
		}
	}

	private static String decodeGZipAttachment(BASE64DecoderStream b64Stream) throws IOException {
		try (
			InputStream is = b64Stream;
			GZIPInputStream gzis = new GZIPInputStream(is);
		) {
			return inputStreamToString(gzis);
		}
	}

	private static ContentType getContentType(Message message) throws MessagingException {
		return new ContentType(message.getContentType());
	}

	private static ContentType getContentType(BodyPart part) throws MessagingException {
		return new ContentType(part.getContentType());
	}

	private static String getFileExt(Message message) throws MessagingException {
		return getFileExt(message.getFileName());
	}

	private static String getFileExt(BodyPart part) throws MessagingException {
		return getFileExt(part.getFileName());
	}

	private static String getFileExt(ZipEntry zipEntry) {
		return getFileExt(zipEntry.getName());
	}

	private static String getFileExt(String fileName) {
		if (fileName == null || fileName.isEmpty()) {
			return null;
		} else {
			int dotIndex = fileName.lastIndexOf('.');
			return (dotIndex == -1)
				? null
				: fileName.substring(dotIndex + 1).toLowerCase();
		}
	}

	private static String inputStreamToString(InputStream is) throws IOException {
		try (var wtr = new StringWriter()) {
			// Don't include the reader as a try resource, because it will prematurely
			// close the ZipInputStream:
			new InputStreamReader(is, StandardCharsets.UTF_8).transferTo(wtr);
			return wtr.toString();
		}
	}
}
