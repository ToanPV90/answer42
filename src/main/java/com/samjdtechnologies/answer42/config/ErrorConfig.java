package com.samjdtechnologies.answer42.config;

import java.util.Map;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.WebRequest;

/**
 * Configuration for custom error handling and error attributes.
 */
@Configuration
public class ErrorConfig {

    /**
     * Customizes the error attributes that will be included in error responses.
     * This replaces the need to configure ServerProperties error settings.
     * 
     * @return a custom implementation of ErrorAttributes that controls error response content
     */
    @Bean
    public ErrorAttributes errorAttributes() {
        return new DefaultErrorAttributes() {
            @Override
            public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
                // Create options that include message but exclude stacktrace
                ErrorAttributeOptions customOptions = ErrorAttributeOptions.defaults()
                        .including(ErrorAttributeOptions.Include.MESSAGE)
                        .including(ErrorAttributeOptions.Include.BINDING_ERRORS);
                
                // Get error attributes with our custom options
                Map<String, Object> errorAttributes = super.getErrorAttributes(webRequest, customOptions);
                
                // You can add or remove additional attributes here if needed
                return errorAttributes;
            }
        };
    }
}
