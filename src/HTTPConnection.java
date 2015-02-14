import java.awt.HeadlessException;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.Set;

public class HTTPConnection implements Runnable {
	// Used to distinguish between different connections in the trace prints
	private static int counter = 0;
	
	private Socket socket;
	private BufferedReader input;
	private DataOutputStream output;
	private File root;
	private String defaultPage;
	private Map<String , Set<String>> policies;
	private int myCounter;
	private PrintWriter writer;
	
	public HTTPConnection(Socket socket, File root, String defaultPage, Map<String, Set<String>> policies, PrintWriter writer) throws IOException {
		this.socket = socket;
		this.root = root;
		this.defaultPage = defaultPage;
		this.policies = policies;
		this.writer = writer;
		input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		output = new DataOutputStream(socket.getOutputStream());
		myCounter = counter++;
	}

	@Override
	public void run() {
		boolean keepAlive = true;
		while(keepAlive) {
			HTTPRequest request  = new HTTPRequest();
				
			try {
				switch(request.parseFirstLine(input)) {
				case 0:
					break; // Parsed OK, continue to headers
				case 400:
					new HTTPResponse(output).generateSpecificResponse(400);
					continue;
				case 501:
					new HTTPResponse(output).generateSpecificResponse(501);
					continue;
				default:
					new HTTPResponse(output).generateSpecificResponse(500);
					continue;
				}
				
				request.readHeaders(input , policies.get(Main.BLOCK_HEADERS));
				
				if(request.checkVersion()) {
					new HTTPResponse(output).generateSpecificResponse(400);
					continue;
				}
				
				//request.parseQuery(false);
				
				switch(request.readBody(input)) {
				case 0:
					break; // Parsed OK
				case 411:
					new HTTPResponse(output).generateSpecificResponse(411);
					continue;
				case 500:
					new HTTPResponse(output).generateSpecificResponse(500);
					continue;
				}
				
				ProxyHandler proxyHandler = new ProxyHandler(request, myCounter);
				
				if(proxyHandler.isSeeLog()){
					new HTTPResponse(output).generateSeeLogResponse(policies);
					continue;
				}
				if(proxyHandler.isNewPolicies()){
					new HTTPResponse(output).editPoliciesAndGenerateResponse(policies , request);
					continue;
				}
				
				
				if(proxyHandler.isEditPolicy()){
					new HTTPResponse(output).generateEditPolicyResponse(policies , "");
					continue;
				}
				if(!proxyHandler.isRequestLegal(policies , writer)) {
					new HTTPResponse(output).generateAccessDeniedResponse(proxyHandler.getRuleBlocked());
					continue;
				}
								
				proxyHandler.connectToHost();
				
				proxyHandler.sendRequest();
				
				proxyHandler.getResponse(output);
				
				proxyHandler.closeConnection();
				
				String keepAliveValue = request.getHeaders().get("connection");
				if(keepAliveValue != null && keepAliveValue.equalsIgnoreCase("close"))
					keepAlive = false;

			} catch (IOException e) {
				// Connection has been closed
				System.out.println(myCounter + " | ### IOException! ###");
				System.out.println(e.getMessage());
				e.printStackTrace();
				break;
			} catch (Exception e) {
				System.out.println(e.getMessage());
				e.printStackTrace();
				try {
					if(output != null)
						new HTTPResponse(output).generateSpecificResponse(500);
				} catch (IOException e1) {
					// Nothing to do
				}
			}	
		}
		System.out.println(myCounter + " | ### Connection is closing ###");
		closeConnection();
	}

	private void closeConnection() {
		try {
			if(input != null)
				input.close();
			if(output != null) {
				output.flush();
				output.close();
			}
			if(socket != null)
				socket.close();
		} catch (IOException e) {
			// Noting to do
		}
	}
}
