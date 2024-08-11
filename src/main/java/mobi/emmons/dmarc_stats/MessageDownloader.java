package mobi.emmons.dmarc_stats;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;

public class MessageDownloader implements AutoCloseable {
	private final String host;
	private final String user;
	private final String password;
	private final String folder;

	private final Store emailStore;
	private final Folder emailFolder;

	public MessageDownloader(String host, String user, String password, String folder) throws MessagingException {
		this.host = Util.requireNonBlank(host, "host");
		this.user = Util.requireNonBlank(user, "user");
		this.password = Util.requireNonBlank(password, "password");
		this.folder = Util.requireNonBlank(folder, "folder");

		emailStore = connectToStore(this.host, this.user, this.password);
		emailFolder = openFolder(emailStore, this.folder);
	}

	public static Store connectToStore(String host, String user, String password) throws MessagingException {
		var p = new Properties();
		p.put("mail.store.protocol", "imaps");
		p.put("mail.host", host);
		p.put("mail.user", user);
		var emailSession = Session.getDefaultInstance(p);

		var store = emailSession.getStore();
		store.connect(user, password);
		return store;
	}

	public static Folder openFolder(Store store, String folder) throws MessagingException {
		var emailFolder = store.getFolder(folder);
		emailFolder.open(Folder.READ_WRITE);
		return emailFolder;
	}

	@Override
	public void close() {
		MessageDownloaderCloseException folderCloseEx = null;
		try {
			if (emailFolder != null) {
				emailFolder.close();
			}
		} catch (MessagingException ex) {
			folderCloseEx = new MessageDownloaderCloseException(ex, "Error closing email folder");
		}

		try {
			if (emailStore != null) {
				emailStore.close();
			}
		} catch (MessagingException ex) {
			if (folderCloseEx != null) {
				folderCloseEx.addSuppressed(ex);
			} else {
				throw new MessageDownloaderCloseException(ex, "Error closing email store");
			}
		}

		if (folderCloseEx != null) {
			throw folderCloseEx;
		}
	}

	public List<MsgInfo> download() throws MessagingException, IOException {
		return download(buildFilter());
	}

	public List<MsgInfo> download(long fromSeconds) throws MessagingException, IOException {
		return download(buildFilter(fromSeconds));
	}

	private List<MsgInfo> download(SearchTerm filter) throws MessagingException, IOException {
		List<MsgInfo> msgInfos = new ArrayList<>();

		Message[] messages = emailFolder.search(filter);
		for (var message : messages) {
			msgInfos.add(new MsgInfo(message));
		}

		return msgInfos;
	}

	public void setMessageSeenFlags(List<MsgInfo> msgInfos) throws MessagingException {
		int[] msgNumbers = msgInfos.stream()
			.mapToInt(MsgInfo::imapMsgNum)
			.toArray();
		var flags = new Flags(Flags.Flag.SEEN);
		emailFolder.setFlags(msgNumbers, flags, true);
	}

	private static SearchTerm buildFilter() {
		return new FlagTerm(new Flags(Flags.Flag.SEEN), false);
	}

	private static SearchTerm buildFilter(Long fromSeconds) {
		Objects.requireNonNull(fromSeconds, "fromSeconds");
		var fromDate = Date.from(Instant.ofEpochSecond(fromSeconds.longValue()));
		return new ReceivedDateTerm(ComparisonTerm.GE, fromDate);
	}
}
