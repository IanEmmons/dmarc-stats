package mobi.emmons.dmarc_stats;

import jakarta.mail.internet.ParseException;

public class UncheckedParseException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public UncheckedParseException(ParseException cause) {
		super(cause);
	}
}
