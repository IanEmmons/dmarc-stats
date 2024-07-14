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
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import mobi.emmons.dmarc_stats.generated.Feedback;

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

	public List<Feedback> getAllReports(boolean updateWithLatestReports) throws IOException {
		return getDownloadedReports();
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
			var db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			var doc = db.parse(new InputSource(is));

			//new XmlNamespaceTranslator()
			//	.addTranslation("", MsgInfo.DMARC_NS)
			//	.translateNamespaces(doc);

			var unmarshaller = JAXBContext.newInstance(Feedback.class).createUnmarshaller();
			return unmarshaller.unmarshal(doc, Feedback.class).getValue();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		} catch (ParserConfigurationException | SAXException | JAXBException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
