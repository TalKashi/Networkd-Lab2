import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * @author kashi
 *
 */
public class WebServer {

	private int port, maxThreads;
	private String defaultPage;
	private File root;
	private Map<String , Set<String>> policies;
	private ServerSocket server;
	private ExecutorService threadsPool;
	private PrintWriter writer;
	public WebServer(File root, String defaultPage, int port, int maxThreads, Map<String, Set<String>> policies, PrintWriter writer) throws IOException {

		this.port = port;
		this.maxThreads = maxThreads;
		this.root = root;
		this.defaultPage = defaultPage;
		this.policies = policies;
		this.writer = writer;
		
		threadsPool = Executors.newFixedThreadPool(this.maxThreads);
		server = new ServerSocket(port);
		System.out.println("Listening port: " + this.port);
	}


	public void run() {
		while(true) {
			try {
				Socket connection = server.accept();
				HTTPConnection HttpConnection = new HTTPConnection(connection, root, defaultPage , policies , writer);
				threadsPool.execute(HttpConnection);

			} catch (IOException e) {
				System.out.println("WARN: Failed to create the new connection");
			}				
		}
	}


}
