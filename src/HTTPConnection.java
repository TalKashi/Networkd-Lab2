import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HTTPConnection implements Runnable {
	private static int counter = 0;
	
	private Socket socket;
	private BufferedReader input;
	private DataOutputStream output;
	private File root;
	private String defaultPage;
	private static Map<String , Set<String>> policies;
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
					//closeConnection();
					continue;
				case 501:
					new HTTPResponse(output).generateSpecificResponse(501);
					//closeConnection();
					continue;
				default:
					new HTTPResponse(output).generateSpecificResponse(500);
					//closeConnection();
					continue;
				}
				
				request.readHeaders(input);
				
				if(request.checkVersion()) {
					new HTTPResponse(output).generateSpecificResponse(400);
					//closeConnection();
					continue;
				}
				
				//request.parseQuery(false);
				
				switch(request.readBody(input)) {
				case 0:
					break; // Parsed OK
				case 411:
					new HTTPResponse(output).generateSpecificResponse(411);
					//closeConnection();
					continue;
				case 500:
					new HTTPResponse(output).generateSpecificResponse(500);
					//closeConnection();
					continue;
				}
				
				ProxyHandler proxyHandler = new ProxyHandler(request, myCounter);

				proxyHandler.connectToHost();
				
				if(!proxyHandler.isRequestLegal(policies , writer)) {
					new HTTPResponse(output).generateSpecificResponse(403);
					//closeConnection();
					continue;
				}
				
				proxyHandler.sendRequest();
				
				proxyHandler.getResponse(output);
				
				proxyHandler.closeConnection();
				
				//HTTPResponse resposne = new HTTPResponse(request, output, root, defaultPage);
				//resposne.generateResposne();
				//keepAlive = request.getHeaders().get("connection").equalsIgnoreCase("keep-alive");

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
