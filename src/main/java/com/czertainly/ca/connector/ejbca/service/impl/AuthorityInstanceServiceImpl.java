package com.czertainly.ca.connector.ejbca.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.attribute.BaseAttribute;
import com.czertainly.api.model.common.attribute.content.FileAttributeContent;
import com.czertainly.api.model.common.attribute.content.SecretAttributeContent;
import com.czertainly.api.model.common.attribute.content.StringAttributeContent;
import com.czertainly.api.model.connector.authority.AuthorityProviderInstanceDto;
import com.czertainly.api.model.connector.authority.AuthorityProviderInstanceRequestDto;
import com.czertainly.api.model.core.credential.CredentialDto;
import com.czertainly.ca.connector.ejbca.config.ApplicationConfig;
import com.czertainly.ca.connector.ejbca.dao.AuthorityInstanceRepository;
import com.czertainly.ca.connector.ejbca.dao.entity.AuthorityInstance;
import com.czertainly.ca.connector.ejbca.rest.EjbcaRestApiClient;
import com.czertainly.ca.connector.ejbca.service.AttributeService;
import com.czertainly.ca.connector.ejbca.service.AuthorityInstanceService;
import com.czertainly.ca.connector.ejbca.ws.EjbcaWS;
import com.czertainly.ca.connector.ejbca.ws.EjbcaWSService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.KeyStoreUtils;
import io.netty.handler.ssl.SslContext;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.ws.BindingProvider;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuthorityInstanceServiceImpl implements AuthorityInstanceService {
    private static final Logger logger = LoggerFactory.getLogger(AuthorityInstanceServiceImpl.class);

    private static final Map<Long, EjbcaWS> connectionsCache = new ConcurrentHashMap<>();
    private static final Map<Long, WebClient> connectionsRestApiCache = new ConcurrentHashMap<>();

    @Value("${ejbca.timeout.connect:500}")
    private int connectionTimeout;

    @Value("${ejbca.timeout.request:1500}")
    private int requestTimeout;

    @Autowired
    private AuthorityInstanceRepository authorityInstanceRepository;

    @Autowired
    private AttributeService attributeService;

    @Override
    public List<AuthorityProviderInstanceDto> listAuthorityInstances() {
        List<AuthorityInstance> authorities;
        authorities = authorityInstanceRepository.findAll();
        if (!authorities.isEmpty()) {
            return authorities
                    .stream().map(AuthorityInstance::mapToDto)
                    .collect(Collectors.toList());
        }
        return null;
    }

    @Override
    public AuthorityProviderInstanceDto getAuthorityInstance(String uuid) throws NotFoundException {
        return authorityInstanceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstance.class, uuid))
                .mapToDto();
    }

    @Override
    public AuthorityProviderInstanceDto createAuthorityInstance(AuthorityProviderInstanceRequestDto request) throws AlreadyExistException {
        if (authorityInstanceRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(AuthorityInstance.class, request.getName());
        }

        if (!attributeService.validateAttributes(
                request.getKind(), request.getAttributes())) {
            throw new ValidationException("Authority instance attributes validation failed.");
        }

        AuthorityInstance instance = new AuthorityInstance();
        instance.setName(request.getName());
        instance.setUrl(AttributeDefinitionUtils.getSingleItemAttributeContentValue("url", request.getAttributes(), StringAttributeContent.class).getData());
        instance.setUuid(UUID.randomUUID().toString());
        CredentialDto credential = AttributeDefinitionUtils.getCredentialContent("credential", request.getAttributes());
        instance.setCredentialUuid(credential.getUuid());
        instance.setCredentialData(AttributeDefinitionUtils.serialize(AttributeDefinitionUtils.responseAttributeConverter(credential.getAttributes())));

        instance.setAttributes(AttributeDefinitionUtils.serialize(AttributeDefinitionUtils.mergeAttributes(attributeService.getAttributes(request.getKind()), request.getAttributes())));

        EjbcaWS connection;
        try {
            connection = createConnection(instance);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new ValidationException(ValidationError.create(ExceptionUtils.getRootCauseMessage(e)));
        }

        authorityInstanceRepository.save(instance);

        try {
            connectionsCache.put(instance.getId(), connection);
        } catch (Exception e) {
            logger.error("Fail to cache connection to CA {} due to error {}", instance.getId(), e.getMessage(), e);
        }

        return instance.mapToDto();
    }

    @Override
    public AuthorityProviderInstanceDto updateAuthorityInstance(String uuid, AuthorityProviderInstanceRequestDto request) throws NotFoundException {
        AuthorityInstance instance = authorityInstanceRepository
                .findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstance.class, uuid));

        if (!attributeService.validateAttributes(
                request.getKind(), request.getAttributes())) {
            throw new ValidationException("Authority instance attributes validation failed.");
        }

        instance.setName(request.getName());
        instance.setUrl(AttributeDefinitionUtils.getSingleItemAttributeContentValue("url", request.getAttributes(), StringAttributeContent.class).getData());

        CredentialDto credential = AttributeDefinitionUtils.getCredentialContent("credential", request.getAttributes());
        instance.setCredentialUuid(credential.getUuid());
        instance.setCredentialData(AttributeDefinitionUtils.serialize(AttributeDefinitionUtils.responseAttributeConverter(credential.getAttributes())));

        instance.setAttributes(AttributeDefinitionUtils.serialize(AttributeDefinitionUtils.mergeAttributes(attributeService.getAttributes(request.getKind()), request.getAttributes())));
        EjbcaWS connection;
        try {
            connection = createConnection(instance);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new ValidationException(ValidationError.create(ExceptionUtils.getRootCauseMessage(e)));
        }

        authorityInstanceRepository.save(instance);

        try {
            connectionsCache.replace(instance.getId(), connection);
        } catch (Exception e) {
            logger.error("Fail to cache connection to CA {} due to error {}", instance.getId(), e.getMessage(), e);
        }

        return instance.mapToDto();
    }

    @Override
    public void removeAuthorityInstance(String uuid) throws NotFoundException {
        AuthorityInstance instance = authorityInstanceRepository
                .findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstance.class, uuid));

        authorityInstanceRepository.delete(instance);

        try {
            connectionsCache.remove(instance.getId());
        } catch (Exception e) {
            logger.error("Fail to cache connection to CA {} due to error {}", instance.getId(), e.getMessage(), e);
        }
    }

    @Override
    public EjbcaWS getConnection(String uuid) throws NotFoundException {
        AuthorityInstance instance = authorityInstanceRepository
                .findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstance.class, uuid));
        return getConnection(instance);
    }

    @Override
    public synchronized EjbcaWS getConnection(AuthorityInstance instance) {
        EjbcaWS port = connectionsCache.get(instance.getId());
        if (port != null) {
            return port;
        }

        port = createConnection(instance);

        try {
            connectionsCache.put(instance.getId(), port);
        } catch (Exception e) {
            logger.error("Fail to cache connection to CA {} due to error {}", instance.getId(), e.getMessage(), e);
        }

        return port;
    }

    private EjbcaWS createConnection(AuthorityInstance instance) {
        EjbcaWSService service = new EjbcaWSService(ApplicationConfig.WSDL_URL);
        EjbcaWS port = service.getEjbcaWSPort();
        final Map<String, Object> requestContext = ((BindingProvider) port).getRequestContext();
        requestContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, instance.getUrl());
        requestContext.put(ApplicationConfig.CONNECT_TIMEOUT, connectionTimeout);
        requestContext.put(ApplicationConfig.REQUEST_TIMEOUT, 1500);
        requestContext.put(ApplicationConfig.REQUEST_SSL_SOCKET_FACTORY, createSSLSocketFactory(instance));

        logger.info("Connected to EJBCA {}", port.getEjbcaVersion());

        return port;
    }

    private SSLSocketFactory createSSLSocketFactory(AuthorityInstance instance) {
        try {
            List<BaseAttribute> attributes = AttributeDefinitionUtils.deserialize(instance.getCredentialData(), BaseAttribute.class);

            KeyManager[] km = null;
            FileAttributeContent keyStoreData = AttributeDefinitionUtils.getSingleItemAttributeContentValue("keyStore", attributes, FileAttributeContent.class);
            if (keyStoreData != null && keyStoreData.getData() != null && keyStoreData.getData().getContent() != null && !keyStoreData.getData().getContent().isEmpty()) {
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); //"SunX509"

                String keyStoreType = AttributeDefinitionUtils.getSingleItemAttributeContentValue("keyStoreType", attributes, StringAttributeContent.class).getData();
                String keyStorePassword = AttributeDefinitionUtils.getSingleItemAttributeContentValue("keyStorePassword", attributes, SecretAttributeContent.class).getData().getSecret();
                byte[] keyStoreBytes = Base64.getDecoder().decode(keyStoreData.getData().getContent());

                kmf.init(KeyStoreUtils.bytes2KeyStore(keyStoreBytes, keyStorePassword, keyStoreType), keyStorePassword.toCharArray());
                km = kmf.getKeyManagers();
            }

            TrustManager[] tm = null;
            FileAttributeContent trustStoreData = AttributeDefinitionUtils.getSingleItemAttributeContentValue("trustStore", attributes, FileAttributeContent.class);
            if (trustStoreData != null && trustStoreData.getData() != null && trustStoreData.getData().getContent() != null && !trustStoreData.getData().getContent().isEmpty()) {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); //"SunX509"

                String trustStoreType = AttributeDefinitionUtils.getSingleItemAttributeContentValue("trustStoreType", attributes, StringAttributeContent.class).getData();
                String trustStorePassword = AttributeDefinitionUtils.getSingleItemAttributeContentValue("trustStorePassword", attributes, SecretAttributeContent.class).getData().getSecret();
                byte[] trustStoreBytes = Base64.getDecoder().decode(trustStoreData.getData().getContent());

                tmf.init(KeyStoreUtils.bytes2KeyStore(trustStoreBytes, trustStorePassword, trustStoreType));
                tm = tmf.getTrustManagers();
            }

            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(km, tm, new SecureRandom());

            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize SSLSocketFactory.", e);
        }
    }

    @Override
    public WebClient getRestApiConnection(String uuid) throws NotFoundException {
        AuthorityInstance instance = authorityInstanceRepository
                .findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstance.class, uuid));
        return getRestApiConnection(instance);
    }

    @Override
    public synchronized WebClient getRestApiConnection(AuthorityInstance instance) {
        WebClient webClient = connectionsRestApiCache.get(instance.getId());
        if (webClient != null) {
            return webClient;
        }

        webClient = createRestApiConnection(instance);

        try {
            connectionsRestApiCache.put(instance.getId(), webClient);
        } catch (Exception e) {
            logger.error("Fail to cache REST API connection to CA {} due to error {}", instance.getId(), e.getMessage(), e);
        }

        return webClient;
    }

    @Override
    public String getRestApiUrl(String authorityInstanceUuid) throws NotFoundException {
        AuthorityInstance instance = authorityInstanceRepository
                .findByUuid(authorityInstanceUuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstance.class, authorityInstanceUuid));

        URL wsUrl = null;
        try {
            wsUrl = new URL(instance.getUrl());
        } catch (MalformedURLException e) {
            logger.error(e.getMessage());
        }

        if (wsUrl == null)
            throw new ValidationException("Invalid or malformed authority instance URL. Authority instance UUID: " + authorityInstanceUuid);

        return "https://" + wsUrl.getHost() + (wsUrl.getPort() != -1 ? ":" + wsUrl.getPort() : "") + "/ejbca/ejbca-rest-api";
    }

    private WebClient createRestApiConnection(AuthorityInstance instance) {
        List<BaseAttribute> attributes = AttributeDefinitionUtils.deserialize(instance.getCredentialData(), BaseAttribute.class);

        /**
         * 1 certificate in response ~ 2000 bytes * 1000 = 2000000
         */
        final int size = 2000 * 1000;
        final ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(size))
                .build();

        SslContext sslContext = EjbcaRestApiClient.createSslContext(attributes);

        HttpClient httpClient = HttpClient.create().secure(t -> t.sslContext(sslContext));

        return WebClient
                .builder()
                .filter(ExchangeFilterFunction.ofResponseProcessor(EjbcaRestApiClient::handleHttpExceptions))
                .exchangeStrategies(strategies)
                .clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }
}
