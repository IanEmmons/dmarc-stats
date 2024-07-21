package mobi.emmons.dmarc_stats;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.angus.mail.util.BASE64DecoderStream;

import jakarta.mail.BodyPart;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.ParseException;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;

public class MessageDownloader {
	private static final ContentType CT_GZIP = newContentType("application/gzip");
	private static final ContentType CT_OCTET_STREAM = newContentType("application/octet-stream");
	private static final ContentType CT_ZIP = newContentType("application/zip");

	private static ContentType newContentType(String type) {
		try {
			return new ContentType(type);
		} catch (ParseException ex) {
			throw new UncheckedParseException(ex);
		}
	}

	public static List<MsgInfo> download(String host, String user, String password,
			String folder, Long fromTime) throws MessagingException, IOException {
		var p = new Properties();
		p.put("mail.store.protocol", "imaps");
		p.put("mail.host", host);
		p.put("mail.user", user);
		var emailSession = Session.getDefaultInstance(p);

		List<MsgInfo> msgInfos = new ArrayList<>();
		try (var store = emailSession.getStore()) {
			store.connect(user, password);

			try (var emailFolder = store.getFolder(folder)) {
				emailFolder.open(Folder.READ_ONLY);

				Message[] messages = (fromTime == null)
					? emailFolder.getMessages()
					: emailFolder.search(buildFilter(fromTime));

				for (var message : messages) {
					var from = Arrays.stream(message.getFrom())
						.map(Object::toString)
						.collect(Collectors.joining(", "));
					var subject = message.getSubject();
					var time = message.getSentDate().toInstant();
					var msgInfo = new MsgInfo(from, subject, time);
					msgInfos.add(msgInfo);
					unpack(message.getContent(), getContentType(message), getFileExt(message), msgInfo);
				}
			}
		}
		return msgInfos;
	}

	private static SearchTerm buildFilter(Long fromTime) {
		Objects.requireNonNull(fromTime, "fromTime");
		SearchTerm dateTerm = new ReceivedDateTerm(ComparisonTerm.GE,
			Date.from(Instant.ofEpochSecond(fromTime.longValue())));
		//SearchTerm flagTerm = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
		return dateTerm;
	}

	private static void unpack(Object content, ContentType contentType, String fileExt, MsgInfo msgInfo)
			throws MessagingException, IOException {
		if (content instanceof MimeMultipart multiPart) {
			for (int i = 0; i < multiPart.getCount(); ++i) {
				BodyPart part = multiPart.getBodyPart(i);
				unpack(part.getContent(), getContentType(part), getFileExt(part), msgInfo);
			}
		} else if (content instanceof BASE64DecoderStream b64Stream) {
			if (CT_ZIP.match(contentType) || "zip".equals(fileExt)) {
				decodeZipAttachment(b64Stream, msgInfo);
			} else if ((CT_GZIP.match(contentType) || CT_OCTET_STREAM.match(contentType)) && "gz".equals(fileExt)) {
				decodeGZipAttachment(b64Stream, msgInfo);
			} else {
				throw new UnexpectedMessageFormatException(
					"Message content has content type %1$s and file extension %2$s",
					contentType.getBaseType(), fileExt);
			}
		} else if (content instanceof String strContent) {
			msgInfo.addBodyPart(strContent);
		} else {
			throw new UnexpectedMessageFormatException(
				"Message content has type %1$s", content.getClass());
		}
	}

	private static void decodeZipAttachment(BASE64DecoderStream b64Stream, MsgInfo msgInfo) throws IOException {
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
					msgInfo.addXmlPart(inputStreamToString(zis));
				} else {
					throw new UnexpectedMessageFormatException(
						"Message content has file extension %1$s", zipEntryExt);
				}
				zis.closeEntry();
			}
		}
	}

	private static void decodeGZipAttachment(BASE64DecoderStream b64Stream, MsgInfo msgInfo) throws IOException {
		try (
			InputStream is = b64Stream;
			GZIPInputStream gzis = new GZIPInputStream(is);
		) {
			msgInfo.addXmlPart(inputStreamToString(gzis));
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
			new InputStreamReader(is, StandardCharsets.UTF_8).transferTo(wtr);
			return wtr.toString();
		}
	}
}
