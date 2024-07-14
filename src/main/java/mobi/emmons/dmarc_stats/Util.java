package mobi.emmons.dmarc_stats;

public class Util {
	private Util() {}	// prevent instantiation

	public static String requireNonBlank(String strParam, String strParamName) {
		if (strParam == null || strParam.isBlank()) {
			throw new IllegalArgumentException(
				"Parameter %1$s must not be blank".formatted(strParamName));
		}
		return strParam;
	}
}
