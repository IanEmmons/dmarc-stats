// https://javaee.github.io/javamail/docs/api

package mobi.emmons.dmarc_stats;

import java.io.IOException;
import java.util.Properties;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.search.FlagTerm;

public class App {
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

	private void run() {
		check(host, user, password, folder);
	}

	public static void check(String host, String user, String password, String folder) {
		var p = new Properties();
		p.put("mail.store.protocol", "imaps");
		p.put("mail.host", host);
		p.put("mail.user", user);
		//p.put("mail.pop3.port", "995");
		//p.put("mail.pop3.starttls.enable", "true");
		var emailSession = Session.getDefaultInstance(p);

		try (var store = emailSession.getStore()) {
			store.connect(user, password);

			try (var emailFolder = store.getFolder(folder)) {
				emailFolder.open(Folder.READ_ONLY);

				Message[] messages = emailFolder.search(
					new FlagTerm(new Flags(Flags.Flag.SEEN), false));
				//Message[] messages = emailFolder.getMessages();
				System.out.println("messages.length---" + messages.length);

				for (int i = 0; i < messages.length; ++i) {
					Message message = messages[i];
					System.out.println("---------------------------------");
					System.out.println("Email Number " + (i + 1));
					System.out.println("Subject: " + message.getSubject());
					System.out.println("From: " + message.getFrom()[0]);
					System.out.println("Text: " + message.getContent().toString());
				}
			}
		} catch (MessagingException | IOException ex) {
			ex.printStackTrace();
		}
	}
}
