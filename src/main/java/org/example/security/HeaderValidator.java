package org.example.security;

import java.util.HashMap;
import java.util.Map;

public class HeaderValidator {
    // security header config
    private final Map<String, String> securityHeaders = new HashMap<>();
    //dangerous header list
    private final String[] dangerousHeaders = {
            "X-Forwarded-Host",
            "X-Forwarded-Proto",
            "X-Real-IP",
    };
    // whether you use strict mode
    private final boolean strictMode;

    public HeaderValidator(boolean strictMode) {
        this.strictMode = strictMode;
        initDefaultHeaders();
    }

    private void initDefaultHeaders() {
        // set default security header
        securityHeaders.put("X-Content-Type-Options", "nosniff");
        securityHeaders.put("X-XSS-Protection", "1; mode=block");
        securityHeaders.put("X-Frame-Options", "SAMEORIGIN");
        securityHeaders.put("Referrer-Policy", "no-referrer-when-downgrade");

        // add more defender in strict mode
        if (strictMode) {
            securityHeaders.put("Content-Security-Policy", "default-src 'self'");
            securityHeaders.put("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
    }
}
