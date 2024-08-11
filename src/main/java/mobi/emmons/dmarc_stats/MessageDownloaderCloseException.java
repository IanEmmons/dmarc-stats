package mobi.emmons.dmarc_stats;

public class MessageDownloaderCloseException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public MessageDownloaderCloseException(Throwable cause, String message) {
		super(message, cause);
	}
}
