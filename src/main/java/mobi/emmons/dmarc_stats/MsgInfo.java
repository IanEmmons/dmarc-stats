package mobi.emmons.dmarc_stats;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import mobi.emmons.dmarc_stats.generated.Feedback;

public class MsgInfo {
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

	public Feedback feedback() throws JAXBException, IOException {
		var numParts = xmlParts.size();
		if (numParts < 1) {
			return null;
		}
		if (numParts > 1) {
			throw new UnexpectedMessageFormatException(
				"Message '%1$s' has %2$d XML attachments"
					.formatted(subject, numParts));
		}
		JAXBContext context = JAXBContext.newInstance(Feedback.class);
		try (Reader rdr = new StringReader(xmlParts.get(0))) {
			return (Feedback) context.createUnmarshaller().unmarshal(rdr);
		}
	}
}
