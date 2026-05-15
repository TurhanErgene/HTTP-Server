
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.util.List;

public class ClientHandler implements Runnable {

    private Socket clientSocket;
    private String publicPath;
    private String clientIP;

    public ClientHandler(Socket socket, String publicPath) {
        this.clientSocket = socket;
        this.publicPath = publicPath;
        this.clientIP = clientSocket.getInetAddress().getHostAddress();
    }

    @Override
    public void run() {
        // in for reading request, out for writing response
        try (
                // InputStream instead of BufferedReader for reading raw bytes and handling both text and binary data (like images) 
                InputStream in = clientSocket.getInputStream(); OutputStream rawOut = clientSocket.getOutputStream(); PrintWriter out = new PrintWriter(rawOut, true)) {

            // İlk satırı (Request Line) StreamUtils ile okuyoruz
            String requestLine = StreamUtils.readLineFromStream(in);
            if (requestLine == null || requestLine.trim().isEmpty()) {
                return;
            }

            int contentLength = 0;
            String boundary = ""; // for multipart/form-data, we need to parse the boundary to separate different parts of the form data
            String headerLine;

            // headers end with an empty line, so we read until we find an empty line. "in" look like this: GET /index.html HTTP/1.1 \r\n Host: localhost \r\n Content-Length: 123 \r\n \r\n (empty line indicates end of headers)
            while (!(headerLine = StreamUtils.readLineFromStream(in)).isEmpty()) {
                if (headerLine.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(headerLine.split(":")[1].trim()); // get the content length for POST request body
                }

                // for file upload, we need to parse the content type to get the boundary for multipart/form-data
                if (headerLine.startsWith("Content-Type:")) {
                    String contentType = headerLine.substring("Content-Type:".length()).trim();
                    if (contentType.contains("multipart/form-data")) {
                        String[] partsInfo = contentType.split(";");
                        for (String part : partsInfo) {
                            if (part.trim().startsWith("boundary=")) {
                                boundary = part.split("=")[1].trim();
                            }
                        }
                    }
                }
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 3) { // method, path, version (GET /index.html HTTP/1.1)
                sendError(out, clientIP, "400 Bad Request");
                return;
            }

            System.out.println("Client Request: " + requestLine + " from " + clientIP); // Debug log

            // Parse the request line ( GET /index.html HTTP/1.1 ) 
            String method = parts[0];
            String requestedPath = parts[1];

            switch (method) {
                case "GET" ->
                    handleGetRequest(requestedPath, out, rawOut);
                case "POST" ->
                    // added boundary
                    handlePostRequest(requestedPath, contentLength, boundary, in, out);

                default ->
                    sendError(out, clientIP, "400 Bad Request");
            }

        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }

    private void handleGetRequest(String path, PrintWriter headerOut, OutputStream dataOut) throws IOException {
        if (path.equals("/test-redirect")) {
            headerOut.println("HTTP/1.1 302 Found");
            headerOut.println("Location: /index.html"); // redirect to index.html
            headerOut.println("Connection: close");
            headerOut.println();
            headerOut.flush();
            System.out.println("Client Request: " + path + " - status code 302 from " + clientIP);
            return;
        }

        // if path is root, serve index.html
        if (path.equals("/")) {
            path = "/index.html";
        }

        File file = new File(publicPath, path);
        if (file.isDirectory()) {
            file = new File(file, "index.html");
        }

        // 2. Security Check: Avoiding Path Traversal 
        if (!file.getCanonicalPath().startsWith(new File(publicPath).getCanonicalPath())) {
            sendError(headerOut, clientIP, "403 Forbidden");
            return;
        }

        if (file.exists() && !file.isDirectory()) {
            byte[] fileBytes = Files.readAllBytes(file.toPath());

            // Send the HTTP response headers
            headerOut.println("HTTP/1.1 200 OK");
            headerOut.println("Content-Type: " + getContentType(file.getName())); // Set the correct content type based on file extension 
            headerOut.println("Content-Length: " + fileBytes.length);
            headerOut.println("Connection: close");
            headerOut.println();
            headerOut.flush();

            // Send the file content as bytes, so the image can be sent correctly
            dataOut.write(fileBytes);
            dataOut.flush();
            System.out.println("Client Request: " + path + " - status code 200 from " + clientIP);
        } else {
            // control if 404.html exists in public directory, if yes serve it, else send a simple 404 message
            File notFoundPage = new File(publicPath, "/404.html");
            if (notFoundPage.exists()) {
                byte[] fileBytes = Files.readAllBytes(notFoundPage.toPath());
                headerOut.println("HTTP/1.1 404 Not Found");
                headerOut.println("Content-Type: text/html");
                headerOut.println("Content-Length: " + fileBytes.length);
                headerOut.println("Connection: close");
                headerOut.println();
                headerOut.flush();
                dataOut.write(fileBytes);
                dataOut.flush();
                System.out.println("Client Request: " + path + " - status code 404 from " + clientIP);
            } else {
                // send a simple 404 message if 404.html is not found
                sendError(headerOut, clientIP, "404 Not Found");
            }
        }
    }

    private String getContentType(String path) {
        if (path.endsWith(".html") || path.endsWith(".htm")) {
            return "text/html";
        }
        if (path.endsWith(".png")) {
            return "image/png";
        }
        return "text/plain";
    }

    private void sendError(PrintWriter out, String clientIP, String status) {
        System.out.println("Response: " + status + " for client " + clientIP);
        out.println("HTTP/1.1 " + status);
        out.println("Content-Type: text/html");
        out.println();
        out.println("<html><body><h1>" + status + "</h1></body></html>");
    }

    private void handlePostRequest(String path, int contentLength, String boundary, InputStream in, PrintWriter out) throws IOException {

        // Login
        if (path.equals("/login")) {
            // convert the body bytes to string for parsing form data
            byte[] bodyBytes = new byte[contentLength];
            StreamUtils.readExactly(in, bodyBytes, contentLength);
            String bodyData = new String(bodyBytes);
            System.out.println("Received POST data: " + bodyData + " from " + clientIP); // Debug log

            // simple parsing: "username=abc&password=123" -> username: abc, password: 123
            String username = "";
            String password = "";
            String[] pairs = bodyData.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    if (kv[0].equals("username")) {
                        username = kv[1];
                    }
                    if (kv[0].equals("password")) {
                        password = kv[1];
                    }
                }
            }

            // authentication
            boolean isAuthenticated = false;

            try {
                File userFile = new File("users.txt");
                if (userFile.exists()) {
                    List<String> lines = Files.readAllLines(userFile.toPath());
                    for (String line : lines) {
                        String[] creds = line.split(":");
                        if (creds.length == 2) {
                            String storedUsername = creds[0].trim(); 
                            String storedPassword = creds[1].trim();
                            if (storedUsername.equals(username) && storedPassword.equals(password)) {
                                isAuthenticated = true;
                                break;
                            }
                        }
                    }
                } else {
                    System.err.println("User file not found: " + userFile.getAbsolutePath());
                }
            } catch (IOException e) {
                System.err.println("Error reading user file: " + e.getMessage());
            }

            if (isAuthenticated) {
                out.println("HTTP/1.1 200 OK");
                out.println("Content-Type: text/html");
                out.println();
                out.println("<h1>Login Successful! Welcome " + username + "</h1>");
                System.out.println("Client " + clientIP + " POST /login - status code 200 (Success)");
            } else {
                out.println("HTTP/1.1 401 Unauthorized");
                out.println("Content-Type: text/html");
                out.println();
                out.println("<h1>401 Unauthorized - Wrong Credentials</h1>");
                out.println("<p>Please try again.</p>");
                out.println("<a href='/login.html'>Go back to login page</a>");
                System.out.println("Client " + clientIP + " POST /login - status code 401 (Failed)");
            }

            // Upload iamge with multipart/form-data
        } else if (path.equals("/upload")) {
            // read the body bytes for multipart/form-data
            byte[] bodyBytes = new byte[contentLength];
            StreamUtils.readExactly(in, bodyBytes, contentLength); // read the raw bytes of the request body, which contains the multipart form data including the image file

            byte[] headerEndPattern = new byte[]{'\r', '\n', '\r', '\n'}; // search for this pattern to find where the image data starts 
            int imageStartIndex = StreamUtils.indexOf(bodyBytes, headerEndPattern, 0); 

            if (imageStartIndex != -1) {
                imageStartIndex += 4; // skip the headerEndPattern to get to the start of the image data

                // where the image data ends? it ends at the boundary which is defined in the Content-Type header
                String endBoundaryStr = "\r\n--" + boundary;
                int imageEndIndex = StreamUtils.indexOf(bodyBytes, endBoundaryStr.getBytes(), imageStartIndex);

                if (imageEndIndex != -1) {
                    int imageLength = imageEndIndex - imageStartIndex;
                    byte[] imageData = new byte[imageLength];
                    System.arraycopy(bodyBytes, imageStartIndex, imageData, 0, imageLength);

                    // save the image to the public directory
                    File savedImage = new File(publicPath, "uploaded_image.png");
                    Files.write(savedImage.toPath(), imageData);

                    out.println("HTTP/1.1 200 OK");
                    out.println("Content-Type: text/html");
                    out.println();
                    out.println("<h1>Image Uploaded Successfully!</h1>");
                    out.println("<a href='/uploaded_image.png'>View Image</a>");
                    System.out.println("Client " + clientIP + " POST /upload - status code 200 (Success)");
                    return;
                }
            }
            sendError(out, clientIP, "400 Bad Request");

        } else {
            sendError(out, clientIP, "404 Not Found");
        }
    }
}
