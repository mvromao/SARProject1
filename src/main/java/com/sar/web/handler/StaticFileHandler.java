package com.sar.web.handler;

import com.sar.web.http.Request;
import com.sar.web.http.Response;
import com.sar.server.Main;
import com.sar.web.http.ReplyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import java.util.Base64;

public class StaticFileHandler extends AbstractRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(StaticFileHandler.class);
    private final String baseDirectory;
    private final String homeFileName;
    private final Map<String, String> mimeTypes;

    public StaticFileHandler(String baseDirectory, String homeFileName) {
        this.baseDirectory = baseDirectory;
        this.homeFileName = homeFileName;
        this.mimeTypes = MIME_TYPES;
    }

    private static final Map<String, String> MIME_TYPES = new HashMap<>();
    
    static {
        MIME_TYPES.put(".html", "text/html");
        MIME_TYPES.put(".htm", "text/html");
        MIME_TYPES.put(".css", "text/css");
        MIME_TYPES.put(".js", "text/javascript");
        MIME_TYPES.put(".jpg", "image/jpeg");
        MIME_TYPES.put(".jpeg", "image/jpeg");
        MIME_TYPES.put(".png", "image/png");
        MIME_TYPES.put(".gif", "image/gif");
    }

    @Override
    protected void handleGet(Request request, Response response) {
        String path = request.urlText;
        if (path.equals("/")) {
            path = "/"+homeFileName;
        }

        String fullPath = baseDirectory + path;
        File file = new File(fullPath);
        
        try {
            if(Main.Authorization) {
                if(request.headers.getHeaderValue("Cookie") != null) {
                    request.parseCookies();
                    String authCookie = request.cookies.getProperty("CookieAuth");
                    if (authCookie != null && !authCookie.equals("63753")) {
                        logger.warn("Invalid cookie received: {}", authCookie);
                        response.setHeader("Cause", "Invalid cookie");
                        response.setError(ReplyCode.UNAUTHORIZED, request.version);
                        return;
                    }
                } else {
                    String authHeader = request.headers.getHeaderValue("Authorization");
                    if (authHeader == null || !authHeader.equals("Basic " + Base64.getEncoder().encodeToString(Main.UserPass.getBytes()))) {
                        logger.warn("Authorization failed for file: {}", file.getAbsolutePath());
                        response.setError(ReplyCode.UNAUTHORIZED, request.version);
                        response.setHeader("WWW-Authenticate", "basic");
                        return;
                    }
                }
            }
            
            response.addCookie("CookieAuth=63753");

            DateFormat httpformat = new SimpleDateFormat("EE, d MMM yyyy HH:mm:ss zz", Locale.UK);
            httpformat.setTimeZone(TimeZone.getTimeZone("GMT"));
            String ifModifiedSince = request.headers.getHeaderValue("If-Modified-Since");
            if (ifModifiedSince != null) {
                Date headerModifiedDate = httpformat.parse(ifModifiedSince);
                if(headerModifiedDate.getTime() >= file.lastModified()) {
                    logger.info("File not modified since last request: {}", fullPath);
                    response.setCode(ReplyCode.NOTMODIFIED);
                    response.setVersion(request.version);
                    return;
                }
            }
            
            if (file.exists() && file.isFile()) {
                response.setCode(ReplyCode.OK);
                response.setVersion(request.version);
                response.setFile(file);
                // set file headers

                response.setHeader("Content-Type", getMimeType(path));
                response.setHeader("Content-Length", String.valueOf(file.length()));
                response.setHeader("Last-Modified", httpformat.format(new Date(file.lastModified())));
                
                logger.info("Serving file: {}", fullPath);
            } else {
                logger.warn("File not found: {}. Returning 404 error.", fullPath);
                response.setCode(ReplyCode.NOTFOUND);
                response.setVersion(request.version);
            }
        } catch (Exception e) {
            logger.error("Error handling GET request for file: {}", fullPath, e);
            response.setError(ReplyCode.BADREQ, request.version);
        }
    }

    @Override
    protected void handlePost(Request request, Response response) {
        // Static files don't handle POST requests
        logger.error("StaticFileHandler does not handle POST requests.");
        response.setError(ReplyCode.NOTIMPLEMENTED, request.version);
    }

    private String getMimeType(String path) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex > 0) {
            String extension = path.substring(dotIndex).toLowerCase();
            return mimeTypes.getOrDefault(extension, DEFAULT_MIME_TYPE);
        }
        return DEFAULT_MIME_TYPE;
    }
}