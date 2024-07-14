package mobi.emmons.dmarc_stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.MissingResourceException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import mobi.emmons.dmarc_stats.generated.Feedback;

class XmlNamespaceTranslatorTest {
	private static final String EXAMPLE_XML = "example-dmarc-report.xml";
	private static final String EXPECTED_OUTPUT = "dmarc-report-fixed.xml";
	private static final Charset CHAR_SET = StandardCharsets.UTF_8;

	@SuppressWarnings("static-method")
	@Test
	void translatorWorks() throws ParserConfigurationException, SAXException, IOException,
			ReflectiveOperationException {
		var db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		try (var is = getRsrcAsStream(EXAMPLE_XML)) {
			var doc = db.parse(new InputSource(is));

			new XmlNamespaceTranslator()
				.addTranslation("", MsgInfo.DMARC_NS)
				.translateNamespaces(doc);

			var actualOutput = write(doc);
			var expectedOutput = getRsrcAsString(EXPECTED_OUTPUT);

			assertEquals(expectedOutput, actualOutput);
		}
	}

	@SuppressWarnings("static-method")
	@Test
	void jaxbWorks() throws ParserConfigurationException, SAXException, IOException,
			JAXBException {
		var db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		try (var is = getRsrcAsStream(EXAMPLE_XML)) {
			var doc = db.parse(new InputSource(is));

			//new XmlNamespaceTranslator()
			//	.addTranslation("", MsgInfo.DMARC_NS)
			//	.translateNamespaces(doc);

			var unmarshaller = JAXBContext.newInstance(Feedback.class).createUnmarshaller();
			var feedback = unmarshaller.unmarshal(doc, Feedback.class).getValue();

			assertEquals("Outlook.com", feedback.getReportMetadata().getOrgName());
			assertEquals("westinefamily.com", feedback.getPolicyPublished().getDomain());
			assertEquals(2, feedback.getRecord().size());
		}
	}

	private static InputStream getRsrcAsStream(String rsrcName) {
		var cl = Thread.currentThread().getContextClassLoader();
		var is = cl.getResourceAsStream(rsrcName);
		if (is == null) {
			throw new MissingResourceException("Unable to load resource", null, rsrcName);
		}
		return is;
	}

	private static String getRsrcAsString(String rsrcName) throws IOException {
		try (
			var is = getRsrcAsStream(rsrcName);
			var rdr = new InputStreamReader(is, CHAR_SET);
			var wtr = new StringWriter();
		) {
			rdr.transferTo(wtr);
			return wtr.toString();
		}
	}

	private static String write(Document doc) throws ReflectiveOperationException, IOException {
		var registry = DOMImplementationRegistry.newInstance();
		var impl = (DOMImplementationLS) registry.getDOMImplementation("XML 3.0 LS 3.0");
		if (impl == null) {
			throw new RuntimeException("No DOMImplementation found!");
		}

		var serializer = impl.createLSSerializer();
		serializer.setNewLine(System.lineSeparator());
		var output = impl.createLSOutput();
		output.setEncoding(CHAR_SET.name());
		try (var wtr = new StringWriter()) {
			output.setCharacterStream(wtr);
			serializer.write(doc, output);
			return wtr.toString();
		}
	}
}
