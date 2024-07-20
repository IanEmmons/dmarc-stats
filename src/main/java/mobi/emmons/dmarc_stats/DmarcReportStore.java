package mobi.emmons.dmarc_stats;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import jakarta.mail.MessagingException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import mobi.emmons.dmarc_stats.generated.DateRangeType;
import mobi.emmons.dmarc_stats.generated.Feedback;
import mobi.emmons.dmarc_stats.generated.ReportMetadataType;

public class DmarcReportStore {
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

	public List<Feedback> getAllReports(boolean updateWithLatestReports) throws IOException, MessagingException {
		var reports = getDownloadedReports();
		if (updateWithLatestReports) {
			var latestReportTime = getLatestReportTime(reports);
			var newReports = downloadReportsAfter(latestReportTime);
			newReports.forEach(this::writeReportToStorage);
			reports.addAll(newReports);
		}
		return reports;
	}

	private List<Feedback> getDownloadedReports() throws IOException {
		try (var fs = FileSystems.getDefault()) {
			var matcher = fs.getPathMatcher("glob:dmarc-*.xml");
			BiPredicate<Path, BasicFileAttributes> predicate = (path, attrs) -> {
				return attrs.isRegularFile() && matcher.matches(path);
			};
			try (var stream = Files.find(storageDir.toPath(), Integer.MAX_VALUE, predicate, FileVisitOption.FOLLOW_LINKS)) {
				return stream
					.map(Path::toFile)
					.map(DmarcReportStore::parseReport)
					.toList();
			}
		}
	}

	private static Feedback parseReport(File reportFile) {
		try (var is = new FileInputStream(reportFile)) {
			var dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(false);
			var doc = dbf.newDocumentBuilder().parse(new InputSource(is));

			// MsgInfo.translateNamespaces(doc);

			var unmarshaller = JAXBContext.newInstance(Feedback.class).createUnmarshaller();
			return unmarshaller.unmarshal(doc, Feedback.class).getValue();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		} catch (ParserConfigurationException | SAXException | JAXBException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static Long getLatestReportTime(List<Feedback> reports) {
		Comparator<Long> comparator = Comparator.naturalOrder();
		return reports.stream()
			.map(Feedback::getReportMetadata)
			.map(ReportMetadataType::getDateRange)
			.map(DateRangeType::getEnd)
			.max(comparator)
			.orElse(null);
	}

	private List<Feedback> downloadReportsAfter(Long time) throws MessagingException, IOException {
		return MessageDownloader
			.download(emailHost, emailUser, emailPassword, emailFolder, time)
			.stream()
			.map(MsgInfo::feedback)
			.toList();
	}

	private void writeReportToStorage(Feedback feedback) {
		try {
			var filePath = new File(storageDir, "dmarc-%1$s.xml".formatted(
				feedback.getReportMetadata().getReportId()));
			var marshaller = JAXBContext.newInstance(Feedback.class).createMarshaller();
			marshaller.marshal(feedback, filePath);
		} catch (JAXBException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
