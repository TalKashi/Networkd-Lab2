import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class HTTPResponse {

	public enum ContentType {
		HTML, PICTURE, ICON, OTHER
	}

	private final static String BAD_REQUEST_MSG ="400 Bad Request";
	private static final String ACCESS_DENIED_MSG = "403 Access Denied";
	private final static String NOT_FOUND_MSG ="404 Not Found";
	private final static String LENGTH_REQUIRED_MSG ="411 Length Required";
	private final static String INTERNAL_ERROR_MSG ="500 Internal Server Error";
	private final static String NOT_IMPLEMENTED_MSG ="501 Not Implemented";
	private final static String MOVED_PERMANENTLY_MSG = "301 Moved Permanently";
	private final static String CRLF = "\r\n";
	private final static String HTTP_VERSION = "HTTP/1.1";
	private final static String OK = HTTP_VERSION + " 200 OK" + CRLF;
	private final static String MOVED_PERMANENTLY = HTTP_VERSION + " " + MOVED_PERMANENTLY_MSG + CRLF;
	private final static String NOT_FOUND = HTTP_VERSION + " " + NOT_FOUND_MSG + CRLF;
	private final static String BAD_REQUEST = HTTP_VERSION + " " + BAD_REQUEST_MSG + CRLF;
	private final static String INTERNAL_ERROR = HTTP_VERSION + " " + INTERNAL_ERROR_MSG + CRLF;
	private final static String LENGTH_REQUIRED = HTTP_VERSION + " " + LENGTH_REQUIRED_MSG + CRLF;
	private final static String NOT_IMPLEMENTED = HTTP_VERSION + " " + NOT_IMPLEMENTED_MSG + CRLF;
	private final static String ACCESS_DENIED = HTTP_VERSION + " " + ACCESS_DENIED_MSG + CRLF;
	private final static String CONTENT_TYPE = "Content-Type";
	private final static String CONTENT_LENGTH = "Content-Length";
	private final static String ERROR_BODY = "<HTML><BODY><H1> %s </H1></BODY></HTML>";
	private final static String ACCESS_DENIED_BODY = "<HTML><BODY><H1> %s </H1><p> %s </p></BODY></HTML>";

	private final static int BUFFER_SIZE = 100;	

	private HTTPRequest request;
	private DataOutputStream output;
	private File root;
	private HashMap<String, String> headersMap;
	private String defaultPage;

	public HTTPResponse(HTTPRequest request, DataOutputStream output, File root, String defaultPage) {
		this.request = request;
		this.output = output;
		this.root = root;
		this.defaultPage = defaultPage;
		headersMap = new HashMap<String, String>();
	}

	public HTTPResponse(DataOutputStream output) {
		this.output = output;
		headersMap = new HashMap<String, String>();
	}

	public void generateSpecificResponse(int responseCode) throws IOException {
		switch (responseCode) {
		case 500:
			sendResponse(INTERNAL_ERROR, String.format(ERROR_BODY, INTERNAL_ERROR_MSG), true);
			break;
		case 501:
			sendResponse(NOT_IMPLEMENTED, String.format(ERROR_BODY, NOT_IMPLEMENTED_MSG), true);
			break;
		case 400:
			sendResponse(BAD_REQUEST, String.format(ERROR_BODY, BAD_REQUEST_MSG), true);
			break;
		case 404:
			sendResponse(NOT_FOUND, String.format(ERROR_BODY, NOT_FOUND_MSG), true);
			break;
		case 411:
			sendResponse(LENGTH_REQUIRED, String.format(ERROR_BODY, LENGTH_REQUIRED_MSG), true);
			break;
		case 301:
			sendResponse(MOVED_PERMANENTLY, String.format(ERROR_BODY, MOVED_PERMANENTLY_MSG), true);
			break;
		}
	}

	public void generateAccessDeniedResponse(String rule) throws IOException {
		sendResponse(ACCESS_DENIED, String.format(ACCESS_DENIED_BODY, ACCESS_DENIED_MSG, rule), true);
	}

	public void generateSeeLogResponse(Map<String, Set<String>> policies) throws IOException {
		String body = "<html><h1>See Log</h1>";
		String line = null;
		BufferedReader input = new BufferedReader(new FileReader(Main.logFile));
		while((line = input.readLine()) != null) {
			body += line + "<br>";
		}
		input.close();
		body += "</html>";
		sendResponse(OK, body, true);

	}

	public void generateEditPolicyResponse(Map<String, Set<String>> policies , String msg) throws IOException {
		String body = "<html><h1>Edit Policies</h1><span  style='color:#ff0000'>" + msg +"</span><form action='/new_policies' method='POST' >"
				+ "<textarea name='textarea' rows='10' cols='50'>"; 
		for(String policy : policies.keySet()){
			for(String rule : policies.get(policy)){
				body += policy + " \"" + rule + "\"" + CRLF;
			}
		}
		body += "</textarea><br>" +
				"<submit><input type=\"submit\" value=\"Submit\">"
				+ "</form></html>";
		sendResponse(OK, body, true);
	}

	public void editPoliciesAndGenerateResponse(Map<String, Set<String>> policies, HTTPRequest request, HTTPConnection httpConnection) throws IOException {

		String body = request.getBody();
		body = body.substring(9, body.length());
		body = body.replaceAll("\\+", " ");
		body = body.replaceAll("%22", "\"");
		body = body.replaceAll("%2F", "/");
		body = body.replaceAll("%0D%0A" , ",");
		String[] allNewPolicies = body.split(",");
		for(String newPolicy : allNewPolicies){
			if(!isPolicyValid(newPolicy)){
				generateEditPolicyResponse(policies, "The Policies You entered were not valid");
				return;
			}
		}
		
		File policeiesFile = new File(Main.policyFile);
		PrintWriter writer = new PrintWriter(policeiesFile);
		for(String newPolicy : allNewPolicies){
			writer.append(newPolicy + CRLF);
		}
		writer.flush();
		writer.close();
		Map<String, Set<String>> newPolicies = Main.readPolicyFile();
		httpConnection.setPolicies(newPolicies);		
		
		generateEditPolicyResponse(newPolicies, "");
	}

	private boolean isPolicyValid(String policy) {
		String[] policyAndBody = policy.split(" ");
		if(policyAndBody.length != 2){
			return false;
		}else if(!policyAndBody[1].startsWith("\"") || !policyAndBody[1].endsWith("\"")){
			return false;	
		}
		policyAndBody[1].replaceAll("\"", "");
		if(!policyAndBody[0].equals(Main.BLOCK_SITE) && !policyAndBody[0].equals(Main.BLOCK_RESOURCE) && 
				!policyAndBody[0].equals(Main.BLOCK_IP_MASK) && !policyAndBody[0].equals(Main.WHITE_LIST) ){
			return false;
		}
		//TODO: End this function
		/*else if(policyAndBody[0].equals(Main.BLOCK_RESOURCE) && !policyAndBody[1].matches("\\..")){
			return false;
		}else if(policyAndBody[0].equals(Main.BLOCK_IP_MASK) && !policyAndBody[1].matches("[0-9]}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}/[0-9]{1,2}")){
			return false;
		}*/
		return true;
	}

	public void generateResposne() throws IOException {
		switch(request.getMethod()) {
		case OPTIONS:
			handleOptionRequest();
			break;
		case TRACE:
			handleTraceRequest();
			break;
		default:
			handleRequest();
		}
	}



	private void handleRequest() throws IOException {
		String path = request.getPath();
		System.out.println("Path is: " + path);
		File requestedFile = null;
		boolean sendBody = request.getMethod() != Method.HEAD;

		if(path.isEmpty() || path.equals("/")) {
			System.out.println("Default is set to: " + defaultPage);
			generateSpecificResponse(301);
			return;
			//			requestedFile = new File(root.getAbsolutePath() + "\\" + defaultPage);
			//			path = defaultPage;
		} else if(path.equalsIgnoreCase("/params_info.html")) {
			handleParamsInfo(sendBody);
			return;
		} else {
			requestedFile = new File(root.getAbsolutePath() + "\\" + path);
		}

		if(!requestedFile.isFile()) {
			System.out.println("FILE WAS NOT FOUND");
			generateSpecificResponse(404);
			return;
		}

		String fileExtension;
		ContentType type;
		try {
			fileExtension = path.substring(path.lastIndexOf(".") + 1, path.length());
			type = checkFileExtension(fileExtension.toLowerCase());
		} catch(StringIndexOutOfBoundsException e) {
			type = ContentType.OTHER;
			System.out.println("File doesn't have '.' in it");
		}

		System.out.println("Type is: " + type.toString());
		String chunked = request.getHeaders().get("chunked");
		if(chunked != null && chunked.equalsIgnoreCase("yes"))
			sendResponseFile(requestedFile, type, sendBody, true);
		else
			sendResponseFile(requestedFile, type, sendBody, false);
	}

	private void handleParamsInfo(boolean sendBody) throws IOException {
		String html = buildHtmlPage();
		sendResponse(OK, html, sendBody);
	}

	private String buildHtmlPage() {
		String html = "<HTML>" +
				"<HEAD><TITLE>Computer networks</TITLE></HEAD>" +
				"<BODY\">" +
				"<table border=\"1\" style=\"width:100%\">";
		HashMap<String, String> paramsMap = request.getParamsMap();
		for(String key : request.getParamsMap().keySet()){
			html +="<tr><td style=\"width:50%\">"+ key +"</td><td >" + paramsMap.get(key) + "</td></tr>";
		}

		html += "</table></BODY></HTML>";
		return html;
	}

	private ContentType checkFileExtension(String extension) {
		switch(extension) {
		case "bmp":
		case "gif":
		case "png":
		case "jpg":
			return ContentType.PICTURE;
		case "html":
			return ContentType.HTML;
		case "ico":
			return ContentType.ICON;
		default:
			return ContentType.OTHER;
		}
	}

	private void handleTraceRequest() throws IOException {

		StringBuilder message = new StringBuilder();

		message.append(request.getFirstLine() + CRLF);
		HashMap<String, String> requestHeaders = request.getHeaders();
		for(String key : requestHeaders.keySet()) {
			message.append(key + ": " + requestHeaders.get(key) + CRLF);
		}

		sendResponse(OK, message.toString(), true);
	}
	private void handleOptionRequest() throws IOException {	

		StringBuilder message = new StringBuilder();
		Method[] methodsArr = Method.values();
		for(int i = 0; i < methodsArr.length - 1; i++) {
			message.append(methodsArr[i].toString() + ", ");
		}
		message.append(methodsArr[methodsArr.length - 1].toString());

		headersMap.put("Allow", message.toString());

		sendResponse(OK, "", false);
	}

	private void sendResponse(String status, String body, boolean hasBody) throws IOException {
		System.out.println("##	PRINTING RESPOSNE	##");

		if(status.equals(MOVED_PERMANENTLY)) {
			headersMap.put("Location", "/" + defaultPage);
		}

		headersMap.put(CONTENT_LENGTH, String.valueOf(body.length()));
		headersMap.put(CONTENT_TYPE, "text/html");
		System.out.print(status);
		output.writeBytes(status);

		for (String key : headersMap.keySet()) {
			System.out.println(key + ": " + headersMap.get(key));
			output.writeBytes(key + ": " + headersMap.get(key) + CRLF);
		}
		System.out.println("");
		output.writeBytes(CRLF);

		if(hasBody)
			output.writeBytes(body + CRLF);
	}

	private void sendResponseFile(File file, ContentType type, boolean sendBody, boolean chunked) throws IOException {
		System.out.println("##	PRINTING RESPOSNE	##");

		switch(type) {
		case PICTURE:
			headersMap.put(CONTENT_TYPE, "image");
			break;
		case HTML:
			headersMap.put(CONTENT_TYPE, "text/html");
			break;
		case ICON:
			headersMap.put(CONTENT_TYPE, "icon");
			break;
		default:
			headersMap.put(CONTENT_TYPE, "application/octet-stream");
		}

		if(!chunked)
			headersMap.put(CONTENT_LENGTH, String.valueOf(file.length()));
		else
			headersMap.put("Transfer-Encoding", "Chunked");

		System.out.print(OK);
		output.writeBytes(OK);

		for (String key : headersMap.keySet()) {
			System.out.println(key + ": " + headersMap.get(key));
			output.writeBytes(key + ": " + headersMap.get(key) + CRLF);
		}
		System.out.println("");
		output.writeBytes(CRLF);

		if(sendBody) {
			if(chunked) {
				sendChunked(file);
			} else {
				byte[] fileBytes = readFile(file);
				output.write(fileBytes);
				output.writeBytes(CRLF);
			}
		}
	}

	private void sendChunked(File file) throws IOException {
		FileInputStream input = new FileInputStream(file);
		byte[] buffer = new byte[BUFFER_SIZE];
		int len;

		while ((len = input.read(buffer, 0, BUFFER_SIZE)) != -1) {
			output.writeBytes(Integer.toHexString(len) + CRLF);
			output.write(buffer, 0, len);
			output.writeBytes(CRLF);
		}
		output.writeBytes("0" + CRLF + CRLF);

		input.close();
	}

	private byte[] readFile(File file) throws IOException {
		FileInputStream input = new FileInputStream(file);
		byte[] bFile = new byte[(int) file.length()];

		while(input.available() != 0)
			input.read(bFile, 0, bFile.length);
		input.close();
		return bFile;
	}
}