ProxyServer.java - Check command line arguments and read the config.ini and given policy file (and verify it) and start the the WebServer class
WebServer.java - Starts the ServerSocket listener and waits for connections. Every new connection wraps in HTTPConnections class and put inside the threadsPool class for execution
HTTPConnection.java - Handles input and output to client via HTTPResponse, HTTPRequest and ProxyHandler classes
HTTPRequest.java - Parses and save all needed data of the http request
ProxyHandler.java - Enforces the policies and communicate between client and destination site
HTTPResponse.java - Used to send content-proxy/policies and content-proxy/logs and general errors