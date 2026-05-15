# Simple Multithreaded HTTP Server (Java)  
This project implements a lightweight HTTP server capable of handling multiple clients using Java sockets and threads. The server supports basic GET and POST functionality, static file serving, simple authentication, and image upload via multipart/form‑data.

## Usage:
javac WebServer.java ClientHandler.java

java WebServer 8888 ./public
This will start the web server on port 8888 and serve files from the ./public directory. You can access the server by navigating to http://localhost:8888 in your web browser. Make sure to replace 8888 with the port number you want to use and ./public with the path to your desired directory.
