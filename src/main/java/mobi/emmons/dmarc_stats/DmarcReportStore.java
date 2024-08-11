package mobi.emmons.dmarc_stats;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.glassfish.jaxb.runtime.marshaller.NamespacePrefixMapper;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import jakarta.mail.MessagingException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import mobi.emmons.dmarc_stats.generated.Feedback;

public class DmarcReportStore {
	private static final String DMARC_NS = "http://dmarc.org/dmarc-xml/0.1";

	private final File storageDir;
	private final String emailHost;
	private final String emailUser;
	private final String emailPassword;
	private final String emailFolder;

	public DmarcReportStore(File storageDir, String emailHost, String emailUser,
			String emailPassword, String emailFolder) {
		this.storageDir = Objects.requireNonNull(storageDir, "storageDir");
		this.emailHost = Util.requireNonBlank(emailHost, "emailHost");
		this.emailUser = Util.requireNonBlank(emailUser, "emailUser");
		this.emailPassword = Util.requireNonBlank(emailPassword, "emailPassword");
		this.emailFolder = Util.requireNonBlank(emailFolder, "emailFolder");

		if (!storageDir.exists()) {
			storageDir.mkdirs();
		} else if (!storageDir.isDirectory()) {
			throw new IllegalArgumentException("storageDir must be a directory");
		}
	}

	public List<Feedback> getAllReports() throws IOException, MessagingException {
		var reports = getDownloadedReports();
		System.out.format("Num reports in store: %1$d%n", reports.size());

		var counter = new AtomicLong();
		try (var downloader = new MessageDownloader(emailHost, emailUser, emailPassword,
				emailFolder, MessageDownloader.OpenMode.READ_WRITE)) {
			var msgInfos = downloader.download();
			msgInfos.stream()
				.map(MsgInfo::xmlPart)
				.map(DmarcReportStore::parseReport)
				.peek(this::writeReportToStorage)
				.peek(feedback -> counter.incrementAndGet())
				.forEach(reports::add);
			downloader.setMessageSeenFlags(msgInfos);
		}

		System.out.format("Downloaded %1$d new reports%n", counter.get());
		return reports;
	}

	public List<Feedback> getDownloadedReports() throws IOException {
		var fs = FileSystems.getDefault();
		var matcher = fs.getPathMatcher("glob:dmarc-*.xml");
		BiPredicate<Path, BasicFileAttributes> predicate = (path, attrs) -> {
			return attrs.isRegularFile() && matcher.matches(path.getFileName());
		};
		try (var stream = Files.find(storageDir.toPath(), Integer.MAX_VALUE, predicate, FileVisitOption.FOLLOW_LINKS)) {
			return stream
				.map(Path::toFile)
				.map(DmarcReportStore::parseReport)
				.collect(Collectors.toCollection(ArrayList::new));
		}
	}

	private static Feedback parseReport(File reportFile) {
		try (var rdr = new FileReader(reportFile, StandardCharsets.UTF_8)) {
			return parseReport(rdr);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public static Feedback parseReport(String reportXml) {
		try (var rdr = new StringReader(reportXml)) {
			return parseReport(rdr);
		}
	}

	private static Feedback parseReport(Reader reportRdr) {
		try {
			var dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(false);
			var doc = dbf.newDocumentBuilder().parse(new InputSource(reportRdr));

			// translateNamespaces(doc);

			var unmarshaller = JAXBContext.newInstance(Feedback.class).createUnmarshaller();
			return unmarshaller.unmarshal(doc, Feedback.class).getValue();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		} catch (ParserConfigurationException | SAXException | JAXBException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static void translateNamespaces(Document doc) {
		new XmlNamespaceTranslator()
			.addTranslation("", DMARC_NS)
			.translateNamespaces(doc);
	}

	private void writeReportToStorage(Feedback feedback) {
		try {
			var filePath = new File(storageDir, "dmarc-%1$s.xml".formatted(
				feedback.getReportMetadata().getReportId()));
			var marshaller = JAXBContext.newInstance(Feedback.class).createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			marshaller.setProperty("org.glassfish.jaxb.namespacePrefixMapper", new NamespacePrefixMapper() {
				@Override
				public String getPreferredPrefix(String nsUri, String suggestion, boolean requirePrefix) {
					return "http://dmarc.org/dmarc-xml/0.1".equals(nsUri)
						? "dmarc"
						: null;
				}
			});
			marshaller.marshal(feedback, filePath);
		} catch (JAXBException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
