import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ProxyHandler {

	private static final String CRLF = "\r\n";
	private final static String POLICY_EDIT_SITE = "http://content-proxy/policies";
	private final static String SEE_LOG_SITE = "http://content-proxy/logs";
	private final static String NEW_POLICIES= "http://content-proxy/new_policies";
	private static final int BUFFER_SIZE = 1024;	

	private HTTPRequest request;
	private Socket destination;
	private DataOutputStream output;
	private DataInputStream input;
	private boolean chunked = false;
	private String contentLength = null;
	private int myCounter;
	private String host, path, ruleBlocked;

	/**
	 * Constructor
	 * @param request
	 * @param counter
	 */
	public ProxyHandler(HTTPRequest request, int counter) {
		this.request = request;
		myCounter = counter;
	}

	/**
	 * Check if the Request is legal according to the policies 
	 * @param policies
	 * @param writer
	 * @return
	 * @throws UnknownHostException 
	 */
	public boolean isRequestLegal(Map<String, Set<String>> policies, PrintWriter writer) {
		setHostAndPath();
		//If the white list is not empty then Check if the site is in the list
		if(policies.get(Main.WHITE_LIST).size() > 0){
			return isSiteInWhitelist(policies , writer);
		}
		if(isSiteBlock(policies, writer)){
			return false;
		}
		if(isResourseBlocked(policies, writer)){
			return false;
		}
		if(isIpBlocked(policies, writer)){
			return false;
		}
		return true;
	}

	public void connectToHost() throws UnknownHostException, IOException {
		destination = new Socket(host, 80);
		output = new DataOutputStream(destination.getOutputStream());
		input = new DataInputStream(destination.getInputStream());
	}

	public void sendRequest() throws IOException {
		if(isEditPolicy() || isSeeLog() || isNewPolicies()){
			return;
		}
		System.out.println(myCounter + " | ### Sending request from proxy ###");

		output.writeBytes(request.getMethod().toString() + " " + path + " " + request.getVersion() + CRLF);
		System.out.println(myCounter + " | DEBUG PRINT: " + request.getMethod().toString() + " " + path + " " + request.getVersion());

		HashMap<String, String> headers = request.getHeaders();
		for (String key : headers.keySet()) {
			output.writeBytes(key + ": " + headers.get(key) + CRLF);
		}
		output.writeBytes(CRLF);

		String body = request.getBody();
		if(body != null) {
			output.write(body.getBytes());
		}
		System.out.println(myCounter + " | ### Finished sending request from proxy ###");
	}

	public void getResponse(DataOutputStream clientOutputStream) throws IOException {
		if(isEditPolicy() || isSeeLog() || isNewPolicies()){
			return;
		}

		System.out.println(myCounter + " | ### Getting response from destination host and sending to client ###");
		boolean foundChunkedOrContentLength = false;
		boolean firstLine = true;

		String line;

		// For parsing first line
		String version = null;
		String responseCode= null;
		String response = null;
		
		while((line = readLine()) != null && !line.isEmpty()) {
			System.out.println(line);
			clientOutputStream.writeBytes(line + CRLF);
			if(firstLine) {
				Pattern pattern = Pattern.compile("(http/[0-9.]+)\\s+([0-9]+)\\s+(.+)");
				Matcher matcher = pattern.matcher(line.toLowerCase());
				if(matcher.matches()) {
					version = matcher.group(1);
					responseCode = matcher.group(2);
					response = matcher.group(3);
				}
				firstLine = false;
			}
			if(!foundChunkedOrContentLength) {
				foundChunkedOrContentLength = checkForContentLengthOrChunked(line);
			}
		}
		clientOutputStream.writeBytes(CRLF);
		int bufferSize = 0;

		try {
			if(contentLength != null)
				bufferSize = Integer.parseInt(contentLength);
		} catch (NumberFormatException e) {
			System.out.println(myCounter + " | ERROR: Failed to parse Content-Length header.");
			throw new NumberFormatException(myCounter + " | ERROR: Content-Length was not a number");
		}
		if(bufferSize < 0) {
			throw new NumberFormatException(myCounter + " | ERROR: Content-Length was invalid number");
		}
		if(chunked) {
			System.out.println(myCounter + " | ### Chunked body ###");
			readChunked(clientOutputStream);
		} else if (contentLength != null){
			System.out.println(myCounter + " | ### Body has content length ###");
			byte buffer[] = new byte[bufferSize];
			try {
				input.readFully(buffer, 0, bufferSize);
			} catch(EOFException e) {
				// Do nothing
				System.out.println(myCounter + " | ### EOF have been reached! ###");
			}
			clientOutputStream.write(buffer);

		} else {
			// Read until end of stream
			System.out.println(myCounter + " | ### Unkown body length ###");
			if(responseCode != null && !responseCode.equals("304")) {
				byte buffer[] = new byte[BUFFER_SIZE];
				int len, totalRead = 0;
				while ((len = input.read(buffer, 0, BUFFER_SIZE)) != -1) {
					totalRead += len;
					System.out.println(myCounter + " | DEBUG PRINT: Read total of " + len + " bytes");
					clientOutputStream.write(buffer, 0, len);
				}
				System.out.println(myCounter + " | DEBUG PRINT: Finished reading after " + totalRead + " bytes");
			}			
		}
		clientOutputStream.flush();
		System.out.println(myCounter + " | ### Finished getting response from destination host and sending to client ###");
	}

	public void closeConnection() {
		try {
			if(input != null)
				input.close();
			if(output != null) {
				output.flush();
				output.close();
			}
			if(destination != null)
				destination.close();
		} catch (IOException e) {
			// Noting to do
		}		
	}

	/**
	 * Check if the request asks to go to Edit Policy page
	 * @return
	 */
	public boolean isEditPolicy(){
		if(request.getPath().toLowerCase().equals(POLICY_EDIT_SITE)){
			return true;
		}
		return false;
	}

	/**
	 * Check if the request asks to go to See Log page
	 * @return
	 */
	public boolean isSeeLog() {
		if(request.getPath().toLowerCase().equals(SEE_LOG_SITE)){
			return true;
		}
		return false;
	}

	/**
	 * Check if the request asks to edit the existing policies
	 * @return
	 */
	public boolean isNewPolicies() {
		if(request.getPath().toLowerCase().equals(NEW_POLICIES)){
			return true;
		}
		return false;
	}


	public String getRuleBlocked() {
		return ruleBlocked;
	}

	private boolean isSiteInWhitelist(Map<String, Set<String>> policies, PrintWriter writer) {
		for(String site : policies.get(Main.WHITE_LIST)){
			if(request.getPath().contains(site)){
				return true;
			}
		}
		writeBlockedSiteToFile(request , " \"" + request.getPath() + "\"" + " Not in White List" , writer);
		return false;
	}

	private boolean isSiteBlock(Map<String, Set<String>> policies, PrintWriter writer) {
		for(String site : policies.get(Main.BLOCK_SITE)){
			if(request.getPath().contains(site)){
				writeBlockedSiteToFile(request , Main.BLOCK_SITE + " \"" + site + "\"", writer);
				return true;
			}
		}
		return false;
	}

	private boolean isResourseBlocked(Map<String, Set<String>> policies,
			PrintWriter writer) {
		for(String resource : policies.get(Main.BLOCK_RESOURCE)){
			if(request.getPath().endsWith(resource)){
				writeBlockedSiteToFile(request , Main.BLOCK_RESOURCE + " \"" + resource + "\"", writer);
				return true;
			}
		}
		return false;
	}

	private boolean isIpBlocked(Map<String, Set<String>> policies, PrintWriter writer) {
		InetAddress address;
		byte[] ip;
		int mask;
		try{
			address = InetAddress.getByName(host);
			byte[] rawDestinationIp = address.getAddress();
			for(String ipAndMask : policies.get(Main.BLOCK_IP_MASK)){
				ip = constructByteArrayFromIp(ipAndMask.split("/")[0]);
				if(ip == null)
					continue;
				mask = Integer.parseInt(ipAndMask.split("/")[1]);
				if(shouldBlockIp(rawDestinationIp, ip, mask)) {
					writeBlockedSiteToFile(request , Main.BLOCK_IP_MASK + " \"" + ipAndMask + "\"", writer);
					return true;
				}
			}
		} catch(UnknownHostException e){
			return false;
		}
		return false;
	}

	private byte[] constructByteArrayFromIp(String ip) {
		String[] strArray = ip.split("\\.");
		if(strArray.length != 4) {
			// Illegal ip address
			return null;
		}
		byte[] byteArray = new byte[4];
		for(int i = 0; i < 4; i++) {
			int value = Integer.parseInt(strArray[i]);
			if (value > 255 || value < 0) {
				// Illegal ip address
				return null;
			}
			byteArray[i] = (byte) value;
		}
		return byteArray;
	}

	// Returns true if and only if this ip should be blocked, false otherwise
	private boolean shouldBlockIp(byte[] destinationIp, byte[] illegalIp, int mask) {
		for(int i = 0; i < 4 && mask > 0; i++, mask -= 8) {
			if(mask < 8) {
				destinationIp[i] >>>= (8 - mask);
				illegalIp[i] >>>= (8 - mask);
			}
			if(destinationIp[i] != illegalIp[i]) {
				return false;
			}
		}		
		return true;
	}

	private void setHostAndPath() {
		String pathFromRequest = request.getPath().toLowerCase() + request.getQuery();
		System.out.println(myCounter + " | DEBUG: First line: " + pathFromRequest);
		Pattern pattern = Pattern.compile("http://([^/]*)(/.*)");
		Matcher matcher = pattern.matcher(pathFromRequest);
		if(matcher.matches()) {
			host = matcher.group(1);
			path = matcher.group(2);
		} else {
			System.out.println(myCounter + " | ### Failed to match original path from request! (" + pathFromRequest + ") ###");
			// TODO: throw exception to handle with it
		}
	}

	private void readChunked(DataOutputStream clientOutputStream) throws IOException {
		String chunkSizeHexa = readLine();
		int chunkSize = Integer.parseInt(chunkSizeHexa, 16);
		while(chunkSize != 0) {
			System.out.println(myCounter + " | ### Chunk size before adding 2 = " + chunkSize);
			chunkSize += 2; // +2 for the CRLF
			byte buffer[] = new byte[chunkSize]; 
			input.readFully(buffer, 0, chunkSize);
			clientOutputStream.writeBytes(chunkSizeHexa + CRLF);
			clientOutputStream.write(buffer);
			chunkSizeHexa = readLine();
			chunkSize = Integer.parseInt(chunkSizeHexa, 16);
		}
		clientOutputStream.writeBytes("0" + CRLF + CRLF);
	}

	private boolean checkForContentLengthOrChunked(String line) {
		if(line.toLowerCase().contains("content-length")) {
			contentLength = line.split(": ")[1];
			return true;
		} else if (line.toLowerCase().contains("transfer-encoding") && line.toLowerCase().contains("chunked")) {
			chunked = true;
			return true;
		}
		return false;
	}

	private void writeBlockedSiteToFile(HTTPRequest request , String rule, PrintWriter writer) {
		writer.append("Time of blocking: " + new SimpleDateFormat("dd\\MM\\yyyy HH:mm:ss").format(new Date()) + "\n");
		writer.append("HTTP request:\n");
		writer.append(request.getFirstLine() + "\n");
		for(String key : request.getHeaders().keySet()){
			writer.append("  " + key + ": " + request.getHeaders().get(key) + "\n");
		}
		writer.append("Rule Blocked the request: " + rule + "\n\n").flush();
		ruleBlocked = rule;
	}

	private String readLine() throws IOException {
		StringBuilder msg = new StringBuilder();
		char c;
		while ((c = (char) input.readByte()) >= 0) {
			switch(c) {
			case '\r':
				break;
			case '\n':
				return msg.toString();
			default:
				msg.append(c);
			}
		}
		return msg.toString();
	}
}
