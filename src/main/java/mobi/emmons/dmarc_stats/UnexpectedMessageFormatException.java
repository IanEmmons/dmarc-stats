package mobi.emmons.dmarc_stats;

public class UnexpectedMessageFormatException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public UnexpectedMessageFormatException(String formatStr, Object... args) {
		super(formatStr.formatted(args));
	}

	public UnexpectedMessageFormatException(Throwable cause, String formatStr, Object... args) {
		super(formatStr.formatted(args), cause);
	}
}
