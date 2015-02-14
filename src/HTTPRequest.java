import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTTPRequest {

	private static final String HTTP_11 = "HTTP/1.1";
	private static final String HTTP_10 = "HTTP/1.0";
	private static final String CONTENT_LENGTH = "content-length";

	private static Pattern requestLine = Pattern.compile("(\\S+)\\s+([^\\s?]+)(\\?(\\S+))?\\s+(HTTP/[0-9.]+)");
	private static Pattern headerLine = Pattern.compile("([^:]+):\\s*(.*)");
	private static Pattern queryLine = Pattern.compile("([^&=]+)=([^&=]+)");


	private Method method;
	private String path, query, version, body, firstLine;
	private HashMap<String, String> headersMap, parametersMap;	

	public HTTPRequest () {
		headersMap = new HashMap<String, String>();
		parametersMap = new HashMap<String, String>();
	}


	/**
	 * Check if version is HTTP/1.1 and does NOT have host header
	 * 
	 * @return True if version is HTTP/1.1 and does NOT have host header
	 */
	public boolean checkVersion() {
		System.out.println("iS1.1: " + version.equalsIgnoreCase(HTTP_11));
		System.out.println("hasHost: " + headersMap.containsKey("host"));
		return version.equalsIgnoreCase(HTTP_11) && !headersMap.containsKey("host");
	}

	/**
	 * Read a request body only if it is a POST request and has Content-Length Header
	 * 
	 * @param input
	 * @return 0 if OK, or the response code that should be generated.
	 * @throws IOException 
	 */
	public int readBody(BufferedReader input) throws IOException {
		if(method != Method.POST)
			return 0;

		if(!headersMap.containsKey(CONTENT_LENGTH))
			return 411; // Length Required!

		int bufferSize = -1;
		try {
			bufferSize = Integer.parseInt(headersMap.get(CONTENT_LENGTH));
		} catch (NumberFormatException e) {
			System.out.println("ERROR: Failed to parse Content-Length header.");
			return 500;
		}
		if(bufferSize < 0) {
			return 500;
		}

		char buffer[] = new char[bufferSize];
		input.read(buffer, 0, bufferSize);
		body = new String(buffer);
		//parseQuery(true);

		return 0;
	}


	/**
	 * Parse the query.
	 */
	public void parseQuery(boolean toParseBody) {
		String parseStr = query;
		if(toParseBody) {
			parseStr = body;
		}
		if(parseStr == null)
			return;

		Matcher matcher = queryLine.matcher(parseStr);

		while(matcher.find()) {
			String key = matcher.group(1);
			String value = matcher.group(2);

			try {
				// Use URLDecoder to handle %20 etc
				parametersMap.put(key, URLDecoder.decode(value, "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				// Not supposer to happen
				System.out.println("WARN: Failed to parse one the the parameters in the query.");
				e.printStackTrace();
			}
		}

	}

	/**
	 * Read HTTP request headers
	 * @param input
	 * @param set 
	 * @param policies 
	 * @throws IOException
	 */
	public void readHeaders(BufferedReader input, Set<String> blockedHeaders) throws IOException {
		String line;
		while ((line = input.readLine()) != null && !line.isEmpty()) {
			System.out.println(line);
			Matcher matcher = headerLine.matcher(line);
			if(!matcher.matches())
				continue; // Not a valid header line, skip it
			if(blockedHeaders.contains(line.split(":")[0])){
				continue; // Header should be ignored according to the policies
			}
			headersMap.put(matcher.group(1).toLowerCase(), matcher.group(2).toLowerCase());

		}
		System.out.println("## FINISHED READING HEADERS ##");

	}

	/**
	 * Parse the first line of a HTTP request
	 * 
	 * @param input
	 * @return 0 if OK or the error number to send to the user.
	 * @throws IOException
	 */
	public int parseFirstLine(BufferedReader input) throws IOException {
		String line;
		while (true) {
			line = input.readLine();
			if(line == null)
				return 500;

			if(!line.isEmpty())
				break;
		}
		System.out.println(line);

		Matcher matcher = requestLine.matcher(line);

		if(!matcher.matches()) {
			System.out.println("WARN: Failed to match the method line. returning 400!");
			return 400;
		}

		String methodString = matcher.group(1).toUpperCase();
		try {
			method = Method.valueOf(methodString);
		} catch(IllegalArgumentException e) {
			// Not a supported method. Generate 501
			System.out.println("Not supported method");
			return 501;
		}

		path = matcher.group(2);
		query = matcher.group(4);
		version = matcher.group(5).toUpperCase();

		firstLine = line;

		return 0;		
	}

	public Method getMethod() {
		return method;
	}


	public HashMap<String, String> getHeaders() {
		return headersMap;
	}

	public String getFirstLine() {
		return firstLine;
	}


	public String getPath() {
		return path;
	}


	public HashMap<String, String> getParamsMap() {
		return parametersMap;
	}


	public String getBody() {
		return body;
	}


	public String getVersion() {
		return version;
	}


	public String getQuery() {
		if(query != null) {
			return "?" + query;
		}
		return "";
	}

}
