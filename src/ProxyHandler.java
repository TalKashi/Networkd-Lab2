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
	private final static String BLOCK_SITE = "block-site";
	private final static String BLOCK_RESOURCE = "block-resource";
	private final static String BLOCK_IP_MASK = "block-ip-mask";
	private static final int BUFFER_SIZE = 1024;	

	private HTTPRequest request;
	private Socket destination;
	private DataOutputStream output;
	private DataInputStream input;
	private boolean chunked = false;
	private String contentLength = null;
	private int myCounter;
	private String host, path;

	public ProxyHandler(HTTPRequest request, int counter) {
		this.request = request;
		myCounter = counter;
	}

	public boolean isRequestLegal(Map<String, Set<String>> policies, PrintWriter writer) {
		for(String site : policies.get(BLOCK_SITE)){
			if(request.getPath().contains(site)){
				writeBlockedSiteToFile(request , BLOCK_SITE , writer);
				return false;
			}
		}
		for(String site : policies.get(BLOCK_RESOURCE)){
			if(request.getPath().endsWith(site)){
				writeBlockedSiteToFile(request , BLOCK_RESOURCE , writer);
				return false;
			}
		}	
		InetAddress address;
		String ip = "";
		int mask;
		try {
			address = InetAddress.getByName(host);
			for(String ipAndMask : policies.get(BLOCK_IP_MASK)){
				ip = ipAndMask.split("/")[0];
				mask = Integer.parseInt(ipAndMask.split("/")[1]);
				int ip1 = Integer.parseInt(ip.replaceAll(".", "")) >> mask;
				int ip2 = Integer.parseInt(address.getHostAddress().replaceAll(".", "")) >> mask;
				if(ip1 == ip2){ 
					writeBlockedSiteToFile(request , BLOCK_IP_MASK , writer);
					return false;
				}
	
			}
			
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			System.out.println("Host does not exists");
			e.printStackTrace();
			return false;
		}


		return true;
	}

	public void writeBlockedSiteToFile(HTTPRequest request , String rule, PrintWriter writer) {
		writer.append("Time of blocking: " + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()) + "\n");
		writer.append("HTTP request:\n");
		writer.append(request.getFirstLine());
		for(String key : request.getHeaders().keySet()){
			writer.append("  " + key + ": " + request.getHeaders().get(key) + "\n");
		}
		writer.append("Rule Blocked the request: " + rule + "\n\n").flush();
	}

	public void connectToHost() throws UnknownHostException, IOException {
		setHostAndPath();
		
		destination = new Socket(host, 80);
		output = new DataOutputStream(destination.getOutputStream());
		input = new DataInputStream(destination.getInputStream());
	}

	private void setHostAndPath() {
		String pathFromRequest = request.getPath().toLowerCase() + request.getQuery();
		System.out.println(myCounter + " | DEBUG: Frist line: " + pathFromRequest);
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

	public void sendRequest() throws IOException {
		System.out.println(myCounter + " | ### Sending request from proxy ###");

		// TODO: Check if need to change request path
		//output.writeBytes(request.getFirstLine() + CRLF);
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
		System.out.println(myCounter + " | ### Getting response from destination host and sending to client ###");
		// TODO: Support chunked response?
		boolean foundChunkedOrContentLength = false;
		String line;
		while((line = readLine()) != null && !line.isEmpty()) {
			System.out.println(line);
			clientOutputStream.writeBytes(line + CRLF);
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
			byte buffer[] = new byte[BUFFER_SIZE];
			int len, totalRead = 0;
			while ((len = input.read(buffer, 0, BUFFER_SIZE)) != -1) {
				totalRead += len;
				System.out.println(myCounter + " | DEBUG PRINT: Read total of " + len + " bytes");
				clientOutputStream.write(buffer, 0, len);
			}
			System.out.println(myCounter + " | DEBUG PRINT: Finished reading after " + totalRead + " bytes");
		}
		clientOutputStream.flush();
		System.out.println(myCounter + " | ### Finished getting response from destination host and sending to client ###");
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
