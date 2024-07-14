package mobi.emmons.dmarc_stats;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

// https://docs.oracle.com/javase/8/docs/api/index.html?org/w3c/dom/Document.html
public class XmlNamespaceTranslator {
	private final Map<String, String> translations = new HashMap<>();

	/**
	 * Add a translation from fromNamespace to toNamespace. If translating to (or from)
	 * no namespace, pass an empty string for the corresponding parameter.
	 *
	 * @param fromNamespace The namespace URI in the XML document that you wish to translate to another.
	 * @param toNamespace The namespace URI to which fromNamespace should be translated.
	 * @return {@code this} to enable call chaining.
	 */
	public XmlNamespaceTranslator addTranslation(String fromNamespace, String toNamespace) {
		translations.put(
			Objects.requireNonNull(fromNamespace, "fromNamespace").strip(),
			Objects.requireNonNull(toNamespace, "toNamespace").strip());
		return this;
	}

	public void translateNamespaces(Document xmlDoc) {
		translateNamespaces(xmlDoc, xmlDoc.getDocumentElement());
	}

	private void translateNamespaces(Document xmlDoc, Node node) {
		var nodeType = node.getNodeType();
		var nodeNamespace = node.getNamespaceURI();
		if ((nodeType == Node.ATTRIBUTE_NODE && !node.getNodeName().startsWith("xmlns:"))
				|| nodeType == Node.ELEMENT_NODE) {
			var fromNS = (nodeNamespace == null) ? "" : nodeNamespace;
			var toNS = translations.get(fromNS);
			if (toNS != null) {
				// the reassignment to node is very important. as per javadoc renameNode will
				// try to modify node (first parameter) in place. If that is not possible it
				// will replace that node for a new created one and return it to the caller.
				// if we did not reassign node we will get no children in the loop below.
				node = xmlDoc.renameNode(node, toNS, node.getNodeName());
			}
		}

		// for attributes of this node
		var attributes = node.getAttributes();
		if (attributes != null) {
			for (int i = 0; i < attributes.getLength(); ++i) {
				var attribute = attributes.item(i);
				translateNamespaces(xmlDoc, attribute);
			}
		}

		// for child nodes of this node
		var childNodes = node.getChildNodes();
		if (childNodes != null) {
			for (int i = 0; i < childNodes.getLength(); ++i) {
				var childNode = childNodes.item(i);
				translateNamespaces(xmlDoc, childNode);
			}
		}
	}
}
