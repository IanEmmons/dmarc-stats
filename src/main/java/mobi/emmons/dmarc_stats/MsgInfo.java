package mobi.emmons.dmarc_stats;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import mobi.emmons.dmarc_stats.generated.Feedback;

public class MsgInfo {
	public static final String DMARC_NS = "http://dmarc.org/dmarc-xml/0.1";

	private final String from;
	private final String subject;
	private final Instant time;
	private final List<String> bodyParts;
	private final List<String> xmlParts;

	public MsgInfo(String from, String subject, Instant time) {
		this.from = from;
		this.subject = subject;
		this.time = time;
		bodyParts = new ArrayList<>();
		xmlParts = new ArrayList<>();
	}

	public String from() {
		return from;
	}

	public String subject() {
		return subject;
	}

	public Instant time() {
		return time;
	}

	public List<String> bodyParts() {
		return Collections.unmodifiableList(bodyParts);
	}

	public void addBodyPart(String newPart) {
		if (newPart != null && !newPart.isBlank()) {
			bodyParts.add(newPart);
		}
	}

	public List<String> xmlParts() {
		return Collections.unmodifiableList(xmlParts);
	}

	public void addXmlPart(String newPart) {
		xmlParts.add(newPart);
	}

	public void checkValidity() {
		var numParts = xmlParts.size();
		if (numParts > 1) {
			throw new UnexpectedMessageFormatException(
				"Message '%1$s' has %2$d XML attachments"
					.formatted(subject, numParts));
		}
	}

	// https://stackoverflow.com/questions/29622877/jaxb-ignore-the-namespace-on-unmarshalling
	// https://stackoverflow.com/questions/1492428/javadom-how-do-i-set-the-base-namespace-of-an-already-created-document
	public Feedback feedback() throws JAXBException, IOException, ParserConfigurationException, SAXException {
		checkValidity();
		if (xmlParts.size() < 1) {
			return null;
		}
		var dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(false);
		var db = dbf.newDocumentBuilder();
		var unmarshaller = JAXBContext.newInstance(Feedback.class).createUnmarshaller();
		try (Reader rdr = new StringReader(xmlParts.get(0))) {
			var doc = db.parse(new InputSource(rdr));
			//translateNamespaces(doc);
			return unmarshaller.unmarshal(doc, Feedback.class).getValue();
		}
	}

	@SuppressWarnings("unused")
	private static void translateNamespaces(Document doc) {
		new XmlNamespaceTranslator()
			.addTranslation("", DMARC_NS)
			.translateNamespaces(doc);
	}
}
