// https://javaee.github.io/javamail/docs/api

package mobi.emmons.dmarc_stats;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;

import jakarta.mail.MessagingException;
import mobi.emmons.dmarc_stats.generated.DKIMAuthResultType;
import mobi.emmons.dmarc_stats.generated.SPFAuthResultType;

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
		} catch (Throwable ex) {
			ex.printStackTrace();
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

	private void run() throws MessagingException, IOException {
		var store = new DmarcReportStore(storageDir, host, user, password, emailFolder);
		var reports = store.getAllReports();

		List<List<String>> csvRow = new ArrayList<>();
		reports.stream().forEach(feedback -> {
				feedback.getRecord().stream().forEach(record -> {
					if (record.getAuthResults().getDkim().size() > 1 || record.getAuthResults().getSpf().size() > 1) {
						System.out.format("Found %1$d DKIM and %2$d SPF results in report %3$s%n",
							record.getAuthResults().getDkim().size(),
							record.getAuthResults().getSpf().size(),
							feedback.getReportMetadata().getReportId());
					}
					var dkimDomains = record.getAuthResults().getDkim().stream()
						.map(DKIMAuthResultType::getDomain)
						.collect(Collectors.joining(";"));
					var spfDomains = record.getAuthResults().getSpf().stream()
						.map(SPFAuthResultType::getDomain)
						.collect(Collectors.joining(";"));
					csvRow.add(List.of(
						feedback.getReportMetadata().getReportId(),
						feedback.getReportMetadata().getOrgName(),
						feedback.getPolicyPublished().getDomain(),
						record.getRow().getSourceIp(),
						record.getIdentifiers().getHeaderFrom(),
						dkimDomains,
						spfDomains));
				});
			});
		var csvFormat = CSVFormat.Builder.create()
			.setHeader("Report ID", "Org. Name", "Policy Domain", "Source IP", "Header From", "DKIM Domain", "SPF Domain")
			.setRecordSeparator(System.lineSeparator())
			.build();
		var file = new File("identifier-report.csv");
		try (var printer = csvFormat.print(file, StandardCharsets.UTF_8)) {
			printer.printRecords(csvRow);
		}
	}
}
