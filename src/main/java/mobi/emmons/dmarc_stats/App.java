// https://javaee.github.io/javamail/docs/api

package mobi.emmons.dmarc_stats;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.angus.mail.util.BASE64DecoderStream;
import org.xml.sax.SAXException;

import jakarta.mail.BodyPart;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.ParseException;
import jakarta.mail.search.FlagTerm;
import jakarta.xml.bind.JAXBException;

public class App {
	private static final ContentType CT_ZIP = newContentType("application/zip");
	private static final ContentType CT_OCTET_STREAM = newContentType("application/octet-stream");
	private static final ContentType CT_GZIP = newContentType("application/gzip");

	private static ContentType newContentType(String type) {
		try {
			return new ContentType(type);
		} catch (ParseException ex) {
			throw new UncheckedParseException(ex);
		}
	}

	private final String host;
	private final String user;
	private final String password;
	private final String folder;

	public static void main(String[] args) {
		try {
			App app = new App(args);
			app.run();
		} catch (CmdLineException ex) {
			usage(ex.getMessage());
			System.exit(-2);
		} catch (Throwable ex) {
			ex.printStackTrace();
			System.exit(-1);
		}
	}

	private static void usage(String message)
	{
		System.out.format("%n");
		if (message != null && !message.isEmpty()) {
			System.out.format("%1$s%n%n", message);
		}
		System.out.format("Usage: %1$s <hostname> <user> <password> <folder>%n%n",
			App.class.getName());
	}

	private App(String[] args) throws CmdLineException {
		if (args.length < 4) {
			throw new CmdLineException("Too few arguments");
		} else if (args.length > 4) {
			throw new CmdLineException("Too many arguments");
		}

		host = args[0];
		user = args[1];
		password = args[2];
		folder = args[3];
	}

	private static final boolean SKIP = true;
	private void run() throws MessagingException, IOException, JAXBException,
			ParserConfigurationException, SAXException {
		List<MsgInfo> msgInfos = downloadMsgInfo(host, user, password, folder);
		for (var msgInfo : msgInfos) {
			var feedback = msgInfo.feedback();
			System.out.format("Message from %1$s at %2$s has %3$d records%n",
				msgInfo.from(), msgInfo.time(), feedback.getRecord().size());
		}
		if (!SKIP) {
			for (var msgInfo : msgInfos) {
				for (var xml : msgInfo.xmlParts()) {
					System.out.format("=========================%n%1$s%n", xml);
				}
			}
		}
	}

	public static List<MsgInfo> downloadMsgInfo(String host, String user, String password,
			String folder) throws MessagingException, IOException {
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

				Message[] messages = emailFolder.search(
					new FlagTerm(new Flags(Flags.Flag.SEEN), false));
				//Message[] messages = emailFolder.getMessages();

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
		try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
			is.transferTo(os);
			return os.toString(StandardCharsets.UTF_8);
		}
	}
}
