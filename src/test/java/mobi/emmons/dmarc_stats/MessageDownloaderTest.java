package mobi.emmons.dmarc_stats;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import jakarta.mail.MessagingException;

public class MessageDownloaderTest {
	private static final String HOST = "imappro.zoho.com";
	private static final String USER = "ian@emmons.mobi";
	private static final String EMAIL_FOLDER = "Zoho/DMARC";

	@SuppressWarnings("static-method")
	@Test
	public void testDownload() throws MessagingException, IOException {
		var password = System.getProperty("email-password");
		assertNotNull(password);

		var now = Instant.now();
		var lastWeek = now.minus(5, ChronoUnit.DAYS).getEpochSecond();

		try (var downloader = new MessageDownloader(HOST, USER, password, EMAIL_FOLDER,
				MessageDownloader.OpenMode.READ_ONLY)) {
			var msgInfos = downloader.download(lastWeek);

			System.out.format("Downloaded %1$d messages:%n", msgInfos.size());
			for (var msgInfo : msgInfos) {
				System.out.format("   From %1$s at %2$s%n",
					msgInfo.from(), msgInfo.time());
			}

			for (var msgInfo : msgInfos) {
				System.out.format("=========================%n%1$s%n", msgInfo.xmlPart());
			}

			assertTrue(msgInfos.size() > 0);
		}
	}
}
