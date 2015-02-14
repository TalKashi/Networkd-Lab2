import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Tal Kashi & Tamir Croll
 *
 */
public class ProxyServer {
	private final static String CONFIG_FILE = "config.ini";
	private static String defaultPage = null;
	private static int port = 0;
	private static int maxThreads = 0;
	private static File root = null;
	private static Map<String , Set<String>> policies = new HashMap<String , Set<String>>();
	private static Set<String> blockSite = new HashSet<String>();
	private static Set<String> blockResource = new HashSet<String>();
	private static Set<String> blockIpMask = new HashSet<String>();
	private static Set<String> whiteList = new HashSet<String>();
	private static Set<String> blockedHeaders = new HashSet<String>();
	private static PrintWriter writer;

	public static String policiesFile;
	public final static String BLOCK_SITE = "block-site";
	public final static String BLOCK_RESOURCE = "block-resource";
	public final static String BLOCK_IP_MASK = "block-ip-mask";
	public final static String WHITE_LIST = "white-list";
	public final static String BLOCK_HEADERS = "block-header";
	public static File logFile;
	
	public static void main(String[] args) {
		if(args.length != 1) {
			System.out.println("USAGE: java proxySever [policy_file]");
			System.exit(1);
		}
		policiesFile = args[0];
		try {
			BufferedReader input = new BufferedReader(new FileReader(CONFIG_FILE));
			
			String line = null;
			while((line = input.readLine()) != null) {
				String[] strArray = line.split("=");
				
				// If split does not return 2 values the pattern is not good
				if(strArray.length != 2)
					continue;
				try {
					// Assign only the first correct value
					if(strArray[0].equalsIgnoreCase("port")) {
						if(port <= 0 || port >= 65536)
							port = Integer.parseInt(strArray[1]);
					} else if(strArray[0].equalsIgnoreCase("root")) {
						if(root == null) {
							root = new File(strArray[1]);
							if (!root.isDirectory()) {
								root = null;
								System.out.println("ERROR: The given root folder '" + strArray[1] + "' does not exists or not a folder!");
							}								
						}
					} else if (strArray[0].equalsIgnoreCase("defaultPage")) {
						if(defaultPage == null)
							defaultPage = strArray[1];
					} else if (strArray[0].equalsIgnoreCase("maxThreads")) {
						if(maxThreads <= 0)
							maxThreads = Integer.parseInt(strArray[1]);
					} else if (strArray[0].equalsIgnoreCase("logPath")) {
						if (logFile == null) {
							logFile = new File(strArray[1]);
							if(logFile.createNewFile()) {
								System.out.println("DEBUG: Created new file for logs");
							} else {
								System.out.println("DEBUG: Log file already existed");
							}
							writer = new PrintWriter(logFile);
						}
					}
				} catch(NumberFormatException e) {
					System.out.println("ERROR: Failed to parse the number given to port/maxThreads!");
					// Continue to next line, maybe a correct format
					continue;
				}				
			}
			input.close();
			
		} catch (FileNotFoundException e) {
			System.out.println("ERROR: File '" + CONFIG_FILE + "' was not found! Exiting program.");
			System.exit(1);
		} catch (IOException e) {
			System.out.println("ERROR: Failed to read the config file! Exiting program.");
			System.exit(1);
		}
		
		if(logFile == null || root == null || defaultPage == null || port <= 0 || port >= 65536 || maxThreads <= 0) {
			System.out.println("ERROR: One of the given parameters in the config file is worng or missing! Exiting program.");
			System.exit(1);
		}
		
		readPolicyFile();
		
		WebServer server;
		try {
			server = new WebServer(root, defaultPage, port, maxThreads , policies , writer);
			server.run();
		} catch(IOException e) {
			System.out.println("ERROR: Failed to create ServerSocket! Exiting program.");
			System.exit(1);
		}
		
	}

	public static void readPolicyFile() {
		blockIpMask.clear();
		blockResource.clear();
		blockSite.clear();
		whiteList.clear();
		blockedHeaders.clear();
		policies.clear();
		try {
			BufferedReader input = new BufferedReader(new FileReader(policiesFile));
			String line = null;
			String[] parsedLine;
			while((line = input.readLine()) != null) {
				parsedLine = line.split(" ");
				if(!isPolicyValid(line)){
					System.out.println("ERROR: The Line: '" + line + "' in the policy.ini file can not be parsed");
					continue;
				}
				switch (parsedLine[0].toLowerCase()){
				case BLOCK_SITE:
					blockSite.add(parsedLine[1].replaceAll("\"", "").toLowerCase());
					break;
				case BLOCK_RESOURCE:
					blockResource.add(parsedLine[1].replaceAll("\"", "").toLowerCase());
					break;
				case BLOCK_IP_MASK:
					blockIpMask.add(parsedLine[1].replaceAll("\"", "").toLowerCase());
					break;
				case WHITE_LIST:
					whiteList.add(parsedLine[1].replaceAll("\"", "").toLowerCase());
					break;
				case BLOCK_HEADERS:
					blockedHeaders.add(parsedLine[1].replaceAll("\"", "").toLowerCase());
					break;
				default:
					System.out.println("ERROR: The policy rule: '" + parsedLine[0] + "' does not exists");
				}
			}
			input.close();
			policies.put(BLOCK_SITE, blockSite);
			policies.put(BLOCK_RESOURCE, blockResource);
			policies.put(BLOCK_IP_MASK, blockIpMask);
			policies.put(WHITE_LIST, whiteList);
			policies.put(BLOCK_HEADERS, blockedHeaders);
			
		} catch (FileNotFoundException e) {
			System.out.println("ERROR: File '" + policiesFile + "' was not found! Exiting program.");
			System.exit(1);
		} catch (IOException e) {
			System.out.println("ERROR: Failed to read the policy file! Exiting program.");
			System.exit(1);
		}
	}
	
	public static boolean isPolicyValid(String policy) {
		policy = policy.toLowerCase();
		if(policy.replaceAll(" ", "").equals("")){
			return true;
		}
		String[] policyAndBody = policy.split(" ");
		if(policyAndBody.length != 2){
			return false;
		}else if(!policyAndBody[1].startsWith("\"") || !policyAndBody[1].endsWith("\"")){
			return false;	
		}
		policyAndBody[1] = policyAndBody[1].replaceAll("\"", "");
		if(!policyAndBody[0].equals(BLOCK_SITE) && !policyAndBody[0].equals(BLOCK_RESOURCE) && 
				!policyAndBody[0].equals(BLOCK_IP_MASK) && !policyAndBody[0].equals(WHITE_LIST) && !policyAndBody[0].equals(BLOCK_HEADERS)){
			return false;
		}
		else if(policyAndBody[0].equals(BLOCK_RESOURCE) && !policyAndBody[1].matches("\\..*")){
			return false;
		}
		else if(policyAndBody[0].equals(BLOCK_IP_MASK) && !policyAndBody[1].matches("[0-9][0-9]?[0-9]?\\.[0-9][0-9]?[0-9]?\\.[0-9][0-9]?[0-9]?\\.[0-9][0-9]?[0-9]?/[0-9][0-9]?")){
			return false;
		}
		return true;
	}
	
}
