package org.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// note : HEAD request no request body and response body
public class HeadRequestHandler {
    public void handle(OutputStream output, HttpRequest request) throws IOException {
        Map<String, String> headers = request.headers;
        String path = request.path;

        // content type is empty mean no res
        boolean exists = !validateResExist(path).isEmpty();
        String contentType = validateResExist(path).get(0);
        if (exists) {

        }
    }

    // return content type and content length
    private List<String> validateResExist(String path) {
        if (path.endsWith("/")) {
            path = "/index.html";
        }
        boolean exists = true;
        ArrayList<String> result = new ArrayList<>();

        Path rootPath = Paths.get(HarmarHttpServer.rootDir).toAbsolutePath();
        File file = new File(HarmarHttpServer.normalizePath(rootPath, path).toUri());
        if (file.exists()) {
            exists = true;
        } else {
            exists = false;
        }

        if (exists) {
            result.add(HarmarHttpServer.determineContentType(HarmarHttpServer.normalizePath(rootPath, path)));
            result.add(String.valueOf(file.length()));
            return result;
        } else {
            return result;
        }
    }
}
