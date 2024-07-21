package mobi.emmons.dmarc_stats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Document;

import mobi.emmons.dmarc_stats.generated.Feedback;

public class MsgInfo {
	private static final String DMARC_NS = "http://dmarc.org/dmarc-xml/0.1";

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

	// https://stackoverflow.com/questions/29622877/jaxb-ignore-the-namespace-on-unmarshalling
	// https://stackoverflow.com/questions/1492428/javadom-how-do-i-set-the-base-namespace-of-an-already-created-document
	public Feedback feedback() {
		var numParts = xmlParts.size();
		if (numParts > 1) {
			throw new UnexpectedMessageFormatException(
				"Message '%1$s' has %2$d XML attachments".formatted(subject, numParts));
		} else if (numParts < 1) {
			return null;
		} else {
			return DmarcReportStore.parseReport(xmlParts.get(0));
		}
	}

	public static void translateNamespaces(Document doc) {
		new XmlNamespaceTranslator()
			.addTranslation("", DMARC_NS)
			.translateNamespaces(doc);
	}
}
