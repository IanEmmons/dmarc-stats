// https://javaee.github.io/javamail/docs/api

package mobi.emmons.dmarc_stats;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import jakarta.mail.MessagingException;
import jakarta.xml.bind.JAXBException;

public class App {
	private final File storageDir;
	private final String host;
	private final String user;
	private final String password;
	private final String emailFolder;

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
		System.out.format("Usage: %1$s <hostname> <user> <password> <email-folder>%n%n",
			App.class.getName());
	}

	private App(String[] args) throws CmdLineException {
		if (args.length < 5) {
			throw new CmdLineException("Too few arguments");
		} else if (args.length > 5) {
			throw new CmdLineException("Too many arguments");
		}

		storageDir = new File(args[0]);
		host = args[1];
		user = args[2];
		password = args[3];
		emailFolder = args[4];
	}

	private static final boolean SKIP = true;
	private void run() throws MessagingException, IOException, JAXBException,
			ParserConfigurationException, SAXException {
		List<MsgInfo> msgInfos = MessageDownloader.download(host, user, password, emailFolder);
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
}
