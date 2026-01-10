package org.example;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// http request
public class  HttpRequest {
    String method;
    String path;
    String protocol;
    Map<String, String> headers = new LinkedHashMap<>();
    boolean hasBody;
    byte[] body;
}