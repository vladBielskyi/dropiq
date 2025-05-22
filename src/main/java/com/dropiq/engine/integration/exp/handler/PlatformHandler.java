package com.dropiq.engine.integration.exp.handler;

import com.dropiq.engine.integration.exp.model.Category;
import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.integration.exp.model.UnifiedProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.xml.Jaxb2XmlDecoder;
import org.springframework.http.codec.xml.Jaxb2XmlEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
public abstract class PlatformHandler {

    private static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024;

    protected final WebClient webClient;

    public PlatformHandler(WebClient.Builder webClientBuilder) {
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(MAX_BUFFER_SIZE);

                    configurer.defaultCodecs().jaxb2Decoder(new Jaxb2XmlDecoder());
                    configurer.defaultCodecs().jaxb2Encoder(new Jaxb2XmlEncoder());
                })
                .build();

        this.webClient = webClientBuilder
                .defaultHeader("Accept", "*/*")
                .exchangeStrategies(exchangeStrategies)  // Apply the custom strategies
                .build();
    }

    /**
     * Fetch XML content from URL with robust error handling
     */
    protected Mono<String> fetchXmlFromUrl(String url, Map<String, String> headers) {
        log.info("Fetching XML from URL: {}", url);
        return webClient.get()
                .uri(URI.create(url))
                .headers(httpHeaders -> {
                    if (headers != null) {
                        headers.forEach(httpHeaders::add);
                    }
                })
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(10))
                        .filter(throwable -> {
                            // Only retry on network errors or 5xx responses
                            if (throwable instanceof WebClientResponseException) {
                                WebClientResponseException wcre = (WebClientResponseException) throwable;
                                return wcre.getStatusCode().is5xxServerError();
                            }
                            return true; // Retry on other errors
                        })
                        .doBeforeRetry(signal ->
                                log.warn("Retrying URL fetch, attempt: {}, error: {}",
                                        signal.totalRetries() + 1,
                                        signal.failure().getMessage())
                        )
                )
                .doOnNext(content -> {
                    // Log the first 100 chars of the response for debugging
                    if (content != null) {
                        String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
                        log.debug("Received XML response (preview): {}", preview);
                    }
                })
                .doOnError(e -> log.error("Error fetching XML from URL: {}, error: {}", url, e.getMessage()))
                .onErrorResume(e -> {
                    log.error("Failed to fetch XML from URL: {}, returning empty response", url, e);
                    return Mono.just("<?xml version=\"1.0\" encoding=\"UTF-8\"?><empty/>");
                });
    }

    /**
     * Parse XML content with better error handling
     */
    protected Mono<Document> parseXml(String xmlContent) {
        return Mono.fromCallable(() -> {
                    try {
                        if (xmlContent == null || xmlContent.trim().isEmpty()) {
                            log.warn("Empty XML content received");
                            return createEmptyDocument();
                        }

                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        // Disable external entity resolution for security
                        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

                        DocumentBuilder builder = factory.newDocumentBuilder();
                        return builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
                    } catch (Exception e) {
                        log.error("Error parsing XML content: {}", e.getMessage());
                        return createEmptyDocument();
                    }
                }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("Error in parseXml: {}", e.getMessage());
                    return Mono.fromCallable(this::createEmptyDocument);
                });
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
                    // Skip problematic child nodes
                    log.debug("Error processing child node at index {} for tag {}: {}", i, tagName, e.getMessage());
                }
            }

            // Fall back to regular text content
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
    public abstract Flux<UnifiedProduct> fetchProducts(String url, Map<String, String> headers);

    /**
     * Abstract method to fetch categories from a platform
     */
    public abstract Flux<Category> fetchCategories(String url, Map<String, String> headers);

    /**
     * Get source type
     */
    public abstract SourceType getSourceType();
}