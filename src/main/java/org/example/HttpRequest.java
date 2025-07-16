package org.example;

import java.util.ArrayList;
import java.util.List;

// http request
public class  HttpRequest {
    String method;
    String path;
    String protocol;
    List<String> headers = new ArrayList<>();
    boolean hasBody;
}