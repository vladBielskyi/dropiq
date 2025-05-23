package com.dropiq.engine.integration.exp.handler;

import com.dropiq.engine.integration.exp.model.Category;
import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.integration.exp.model.UnifiedProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public abstract class PlatformHandler {

    protected final RestTemplate restTemplate;

    public PlatformHandler(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetch XML content from URL with error handling
     */
    protected String fetchXmlFromUrl(String url, Map<String, String> headers) {
        log.info("Fetching XML from URL: {}", url);

        int maxRetries = 3;
        int currentRetry = 0;

        while (currentRetry < maxRetries) {
            try {
                HttpHeaders httpHeaders = new HttpHeaders();
                if (headers != null) {
                    headers.forEach(httpHeaders::add);
                }

                HttpEntity<String> entity = new HttpEntity<>(httpHeaders);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    String content = response.getBody();
                    log.debug("Successfully fetched XML content, length: {}", content.length());
                    return content;
                }

                log.warn("Received non-success status: {} from URL: {}", response.getStatusCode(), url);

            } catch (RestClientException e) {
                currentRetry++;
                log.warn("Error fetching XML from URL: {}, attempt {}/{}, error: {}",
                        url, currentRetry, maxRetries, e.getMessage());

                if (currentRetry >= maxRetries) {
                    log.error("Failed to fetch XML from URL after {} attempts: {}", maxRetries, url);
                    throw new RuntimeException("Failed to fetch XML from URL: " + url, e);
                }

                // Wait before retry
                try {
                    Thread.sleep(2000L * currentRetry); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted while waiting for retry", ie);
                }
            }
        }

        throw new RuntimeException("Failed to fetch XML from URL: " + url);
    }

    /**
     * Parse XML content with error handling
     */
    protected Document parseXml(String xmlContent) {
        try {
            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                log.warn("Empty XML content received");
                return createEmptyDocument();
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("Error parsing XML content: {}", e.getMessage());
            try {
                return createEmptyDocument();
            } catch (Exception ex) {
                throw new RuntimeException("Failed to create empty document", ex);
            }
        }
    }

    /**
     * Create an empty XML document as a fallback
     */
    private Document createEmptyDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();
        Element root = doc.createElement("empty");
        doc.appendChild(root);
        return doc;
    }

    /**
     * Get element text content with comprehensive safety checks
     */
    protected String getElementTextContent(Element element, String tagName) {
        try {
            if (element == null || tagName == null) {
                return null;
            }

            NodeList nodeList = element.getElementsByTagName(tagName);
            if (nodeList == null || nodeList.getLength() == 0) {
                return null;
            }

            Node node = nodeList.item(0);
            if (node == null) {
                return null;
            }

            try {
                String content = node.getTextContent();
                return content != null ? content.trim() : null;
            } catch (Exception e) {
                log.debug("Error getting text content for tag {}: {}", tagName, e.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.debug("Error in getElementTextContent for tag {}: {}", tagName, e.getMessage());
            return null;
        }
    }

    /**
     * Get element CDATA content with comprehensive safety checks
     */
    protected String getElementCDataContent(Element element, String tagName) {
        try {
            if (element == null || tagName == null) {
                return null;
            }

            NodeList nodeList = element.getElementsByTagName(tagName);
            if (nodeList == null || nodeList.getLength() == 0) {
                return null;
            }

            Node node = nodeList.item(0);
            if (node == null) {
                return null;
            }

            NodeList childNodes = node.getChildNodes();
            if (childNodes == null) {
                return null;
            }

            // First try to find CDATA sections
            for (int i = 0; i < childNodes.getLength(); i++) {
                try {
                    Node childNode = childNodes.item(i);
                    if (childNode != null && childNode.getNodeType() == Node.CDATA_SECTION_NODE) {
                        String value = childNode.getNodeValue();
                        return value != null ? value.trim() : null;
                    }
                } catch (Exception e) {
                    log.debug("Error processing child node at index {} for tag {}: {}", i, tagName, e.getMessage());
                }
            }

            try {
                String content = node.getTextContent();
                return content != null ? content.trim() : null;
            } catch (Exception e) {
                log.debug("Error getting text content for tag {}: {}", tagName, e.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.debug("Error in getElementCDataContent for tag {}: {}", tagName, e.getMessage());
            return null;
        }
    }

    /**
     * Abstract method to fetch products from a platform
     */
    public abstract List<UnifiedProduct> fetchProducts(String url, Map<String, String> headers);

    /**
     * Abstract method to fetch categories from a platform
     */
    public abstract List<Category> fetchCategories(String url, Map<String, String> headers);

    public abstract List<Category> fetchCategories(String xmlContent);

    /**
     * Get source type
     */
    public abstract SourceType getSourceType();
}