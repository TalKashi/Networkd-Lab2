import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
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

	private DataOutputStream output;
	private HashMap<String, String> headersMap;

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

	public void generateSeeLogResponse(Map<String, Set<String>> policies , HTTPRequest request) throws IOException {
		if(specialMethod(request)){
			return;
		}
		boolean isHead = (request.getMethod().equals(Method.HEAD));
		String body = "<html><h1>See Log</h1>";
		String line = null;
		BufferedReader input = new BufferedReader(new FileReader(ProxyServer.logFile));
		while((line = input.readLine()) != null) {
			body += line + "<br>";
		}
		input.close();
		body += "</html>";
		sendResponse(OK, body, !isHead);
	}

	/**
	 * generates the Edit Policy page
	 * @param policies
	 * @param msg
	 * @throws IOException
	 */
	public void generateEditPolicyResponse(Map<String, Set<String>> policies , String msg , HTTPRequest request) throws IOException {
		if(specialMethod(request)){
			return;
		}
		boolean isHead = (request.getMethod().equals(Method.HEAD));
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
		sendResponse(OK, body, !isHead);
	}



	/**
	 * Get the new policies changes' update them' and returns an updated html to the client
	 * @param policies
	 * @param request
	 * @param httpConnection
	 * @throws IOException
	 */
	public void editPoliciesAndGenerateResponse(Map<String, Set<String>> policies, HTTPRequest request) throws IOException {
	
		String body = request.getBody();
		body = body.substring(9, body.length());
		body = body.replaceAll("\\+", " ");
		body = body.replaceAll("%22", "\"");
		body = body.replaceAll("%2F", "/");
		body = body.replaceAll("%0D%0A" , ",");
	
		String[] allNewPolicies = body.split(",");
		//Check the new policies Entered by the user
		for(String newPolicy : allNewPolicies){
			if(!ProxyServer.isPolicyValid(newPolicy)){
				generateEditPolicyResponse(policies, "The policies you entered were not valid" , request);
				return;
			}
		}
	
		//Write the new policies to the policies file
		File policeiesFile = new File(ProxyServer.policiesFile);
		PrintWriter writer = new PrintWriter(policeiesFile);
		for(String newPolicy : allNewPolicies){
			writer.append(newPolicy + CRLF);
		}
		writer.flush();
		writer.close();
		//Updates the policies Map
		ProxyServer.readPolicyFile();
	
		generateEditPolicyResponse(policies, "" , request);
	}

	private boolean specialMethod(HTTPRequest request ) throws IOException {
		switch(request.getMethod()) {
		case OPTIONS:
			handleOptionRequest();
			return true;
		case TRACE:
			handleTraceRequest(request );
			return true;
		default:
			return false;
		}
	}
	
	private void handleTraceRequest(HTTPRequest request) throws IOException {
		StringBuilder message = new StringBuilder();

		message.append(request.getFirstLine() + CRLF);
		HashMap<String, String> requestHeaders = request.getHeaders();
		for(String key : requestHeaders.keySet()) {
			message.append(key + ": " + requestHeaders.get(key) + CRLF);
		}
		sendResponse(OK, message.toString(), true);
	}
	
	private void handleOptionRequest() throws IOException{
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
}
