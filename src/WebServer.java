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
	private Map<String , Set<String>> policies;
	private ServerSocket server;
	private ExecutorService threadsPool;
	private PrintWriter writer;
	private SitesCache sitesCache;
	
	public WebServer(int port, int maxThreads, Map<String, Set<String>> policies, PrintWriter writer, SitesCache sitesCache) throws IOException {

		this.port = port;
		this.maxThreads = maxThreads;
		this.policies = policies;
		this.writer = writer;
		this.sitesCache = sitesCache;
		
		threadsPool = Executors.newFixedThreadPool(this.maxThreads);
		server = new ServerSocket(port);
		System.out.println("Listening port: " + this.port);
	}


	public void run() {
		while(true) {
			try {
				Socket connection = server.accept();
				HTTPConnection HttpConnection = new HTTPConnection(connection, policies , writer , sitesCache);
				threadsPool.execute(HttpConnection);

			} catch (IOException e) {
				System.out.println("WARN: Failed to create the new connection");
			}				
		}
	}
}
