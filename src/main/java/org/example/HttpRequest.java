package org.example;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// http request
public class  HttpRequest {
    public String method;
    public String path;
    public String protocol;
    public Map<String, String> headers = new LinkedHashMap<>();
    public boolean hasBody;
    byte[] body;
}