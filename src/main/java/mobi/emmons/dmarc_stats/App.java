// https://javaee.github.io/javamail/docs/api

package mobi.emmons.dmarc_stats;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.lang3.tuple.Pair;

import jakarta.mail.MessagingException;
import mobi.emmons.dmarc_stats.generated.DKIMAuthResultType;
import mobi.emmons.dmarc_stats.generated.SPFAuthResultType;

public class App {
	private static final boolean SHOW_ORG_TO_IP_CORRESPONDENCE = false;

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

		List<List<String>> csvRows = new ArrayList<>();
		reports.stream().forEach(feedback -> {
				feedback.getRecord().stream().forEach(record -> {
					if (record.getAuthResults().getDkim().size() > 1 || record.getAuthResults().getSpf().size() > 1) {
						System.out.format("Found %1$d DKIM and %2$d SPF results in report %3$s%n",
							record.getAuthResults().getDkim().size(),
							record.getAuthResults().getSpf().size(),
							feedback.getReportMetadata().getReportId());
					}
					var dkimDomains = record.getAuthResults().getDkim().stream()
						.filter(Objects::nonNull)
						.map(DKIMAuthResultType::getDomain)
						.filter(Util::isNonBlank)
						.collect(Collectors.joining("; "));
					var spfDomains = record.getAuthResults().getSpf().stream()
						.filter(Objects::nonNull)
						.map(SPFAuthResultType::getDomain)
						.filter(Util::isNonBlank)
						.collect(Collectors.joining("; "));
					csvRows.add(
						Stream.of(
							feedback.getReportMetadata().getReportId(),
							feedback.getReportMetadata().getOrgName(),
							feedback.getPolicyPublished().getDomain(),
							record.getRow().getSourceIp(),
							record.getIdentifiers().getEnvelopeTo(),
							record.getIdentifiers().getEnvelopeFrom(),
							record.getIdentifiers().getHeaderFrom(),
							dkimDomains,
							spfDomains)
						.map(Util::nullToEmpty)
						.toList());
				});
			});

		var csvFormat = CSVFormat.Builder.create()
			.setHeader("Report ID", "Org. Name", "Policy Domain", "Source IP",
				"Envelope To", "Envelope From", "Header From", "DKIM Domain", "SPF Domain")
			.setRecordSeparator(System.lineSeparator())
			.build();
		var file = new File("identifier-report.csv");
		try (var printer = csvFormat.print(file, StandardCharsets.UTF_8)) {
			printer.printRecords(csvRows);
		}

		if (SHOW_ORG_TO_IP_CORRESPONDENCE) {
			orgToIpCorrespondence(csvRows);
		}
	}

	private static void orgToIpCorrespondence(List<List<String>> csvRows) {
		// Key is the pair (Source IP, Org Name), value is count:
		Map<Pair<String, String>, Long> ipOrgPairCounts = csvRows.stream()
			.map(row -> Pair.of(row.get(3), row.get(1)))	// (Source IP, Org Name)
			.collect(Collectors.groupingBy(pair -> pair, TreeMap::new, Collectors.counting()));
		// Key is Source IP, value is a set of (Org Name, count) pairs:
		Map<String, Set<Pair<String, Long>>> orgCountsByIP = ipOrgPairCounts.entrySet().stream().collect(
			Collectors.groupingBy(entry -> entry.getKey().getLeft(), TreeMap::new,
				Collectors.mapping(entry -> Pair.of(entry.getKey().getRight(), entry.getValue()),
					Collectors.toCollection(TreeSet::new))));
		printXCountsByY("Organization counts by Source IP", orgCountsByIP);
		// Key is Org Name, value is a set of (Source IP, count) pairs:
		Map<String, Set<Pair<String, Long>>> ipCountsByOrg = ipOrgPairCounts.entrySet().stream().collect(
			Collectors.groupingBy(entry -> entry.getKey().getRight(), TreeMap::new,
				Collectors.mapping(entry -> Pair.of(entry.getKey().getLeft(), entry.getValue()),
					Collectors.toCollection(TreeSet::new))));
		printXCountsByY("Source IP counts by Organization", ipCountsByOrg);
	}

	private static void printXCountsByY(String label, Map<String, Set<Pair<String, Long>>> xCountsByY) {
		System.out.format("%n%1$s:%n", label);
		for (var y : xCountsByY.keySet()) {
			System.out.format("%1$s:%n", y);
			for (var pair : xCountsByY.get(y)) {
				System.out.format("   %1$s - %2$d%n", pair.getLeft(), pair.getRight());
			}
		}
	}
}
