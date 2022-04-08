package com.czertainly.ca.connector.ejbca.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.RequestAttributeDto;
import com.czertainly.api.model.connector.v2.CertificateDataResponseDto;
import com.czertainly.ca.connector.ejbca.api.CertificateControllerImpl;
import com.czertainly.ca.connector.ejbca.service.AuthorityInstanceService;
import com.czertainly.ca.connector.ejbca.service.EjbcaService;
import com.czertainly.ca.connector.ejbca.util.EjbcaUtils;
import com.czertainly.ca.connector.ejbca.ws.*;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.czertainly.ca.connector.ejbca.api.AuthorityInstanceControllerImpl.*;
import static com.czertainly.ca.connector.ejbca.api.AuthorityInstanceControllerImpl.ATTRIBUTE_KEY_RECOVERABLE;

@Service
@Transactional
public class EjbcaServiceImpl implements EjbcaService {

    @Autowired
    private AuthorityInstanceService authorityInstanceService;

    @Override
    public void createEndEntity(String authorityUuid, String username, String password, String subjectDn, List<RequestAttributeDto> raProfileAttributes, List<RequestAttributeDto> issueAttributes) throws NotFoundException, AlreadyExistException {
        EjbcaWS ejbcaWS = authorityInstanceService.getConnection(authorityUuid);

        if (getUser(ejbcaWS, username) != null) {
            throw new AlreadyExistException("End Entity " + username + " already exists");
        }

        UserDataVOWS user = new UserDataVOWS();
        user.setUsername(username);
        user.setPassword(password);
        user.setSubjectDN(subjectDn);
        prepareEndEntity(user, raProfileAttributes, issueAttributes);

        try {
            ejbcaWS.editUser(user);
        } catch (AuthorizationDeniedException_Exception e) {
            throw new AccessDeniedException("Authorization denied on EJBCA", e);
        } catch (CADoesntExistsException_Exception e) {
            throw new NotFoundException("CA", user.getCaName());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void createEndEntity(String authorityUuid, String username, String password, String subjectDn, List<RequestAttributeDto> raProfileAttributes, Map<String, Object> metadata) throws NotFoundException, AlreadyExistException {
        EjbcaWS ejbcaWS = authorityInstanceService.getConnection(authorityUuid);

        if (getUser(ejbcaWS, username) != null) {
            throw new AlreadyExistException("End Entity " + username + " already exists");
        }

        UserDataVOWS user = new UserDataVOWS();
        user.setUsername(username);
        user.setPassword(password);
        user.setSubjectDN(subjectDn);
        prepareEndEntity(user, raProfileAttributes, metadata);

        try {
            ejbcaWS.editUser(user);
        } catch (AuthorizationDeniedException_Exception e) {
            throw new AccessDeniedException("Authorization denied on EJBCA", e);
        } catch (CADoesntExistsException_Exception e) {
            throw new NotFoundException("CA", user.getCaName());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void renewEndEntity(String authorityUuid, String username, String password) throws NotFoundException {
        EjbcaWS ejbcaWS = authorityInstanceService.getConnection(authorityUuid);

        UserDataVOWS user = getUser(ejbcaWS, username);
        if (user == null) {
            throw new NotFoundException("EndEntity", username);
        }

        user.setPassword(password);
        user.setStatus(EndEntityStatus.NEW.getCode());

        try {
            ejbcaWS.editUser(user);
        } catch (AuthorizationDeniedException_Exception e) {
            throw new AccessDeniedException("Authorization denied on EJBCA", e);
        } catch (CADoesntExistsException_Exception e) {
            throw new NotFoundException("CA", user.getCaName());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public CertificateDataResponseDto issueCertificate(String authorityUuid, String username, String password, String pkcs10) throws NotFoundException {
        EjbcaWS ejbcaWS = authorityInstanceService.getConnection(authorityUuid);

        try {
            CertificateResponse certificateResponse = ejbcaWS.pkcs10Request(
                    username,
                    password,
                    pkcs10,
                    null,
                    "PKCS7WITHCHAIN"); // constant for PKCS7 with chain
            CertificateDataResponseDto response = new CertificateDataResponseDto();
            response.setCertificateData(new String(certificateResponse.getData(), StandardCharsets.UTF_8));
            return response;
        } catch (AuthorizationDeniedException_Exception e) {
            throw new AccessDeniedException("Authorization denied on EJBCA", e);
        } catch (CADoesntExistsException_Exception e) {
            throw new NotFoundException("CA", "N/A");
        } catch (NotFoundException_Exception e) {
            throw new NotFoundException("EndEntity", username);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void revokeCertificate(String uuid, String issuerDn, String serialNumber, int revocationReason) throws NotFoundException, AccessDeniedException {
        EjbcaWS ejbcaWS = authorityInstanceService.getConnection(uuid);
        try {
            ejbcaWS.revokeCert(issuerDn, serialNumber, revocationReason);
        } catch (AuthorizationDeniedException_Exception e) {
            throw new AccessDeniedException("Authorization denied on EJBCA " + e.getMessage());
        } catch (CADoesntExistsException_Exception e) {
            throw new NotFoundException("CA of Certificate");
        } catch (NotFoundException_Exception e) {
            throw new NotFoundException("Certificate");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public UserDataVOWS getUser(EjbcaWS ejbcaWS, String username) throws NotFoundException {
        UserMatch userMatch = EjbcaUtils.prepareUsernameMatch(username);

        try {
            List<UserDataVOWS> users = ejbcaWS.findUser(userMatch);
            return (users != null && !users.isEmpty()) ? users.get(0) : null;
        } catch (AuthorizationDeniedException_Exception e) {
            throw new AccessDeniedException("Authorization denied on EJBCA", e);
        } catch (EndEntityProfileNotFoundException_Exception e) {
            throw new NotFoundException("EndEntity", username);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void prepareEndEntity(UserDataVOWS user, List<RequestAttributeDto> raProfileAttrs, List<RequestAttributeDto> issueAttrs) {
        setUserProfiles(user, raProfileAttrs);

        String email = AttributeDefinitionUtils.getAttributeValue(CertificateControllerImpl.ATTRIBUTE_EMAIL, issueAttrs);
        if (StringUtils.isNotBlank(email)) {
            user.setEmail(email);
        }

        String san = AttributeDefinitionUtils.getAttributeValue(CertificateControllerImpl.ATTRIBUTE_SAN, issueAttrs);
        if (StringUtils.isNotBlank(san)) {
            user.setSubjectAltName(san);
        }

        String extension = AttributeDefinitionUtils.getAttributeValue(CertificateControllerImpl.ATTRIBUTE_EXTENSION, issueAttrs);
        setUserExtensions(user, extension);
    }

    private void prepareEndEntity(UserDataVOWS user, List<RequestAttributeDto> raProfileAttrs, Map<String, Object> metadata) {
        setUserProfiles(user, raProfileAttrs);

        String email = (String) metadata.get(CertificateEjbcaServiceImpl.META_EMAIL);
        if (StringUtils.isNotBlank(email)) {
            user.setEmail(email);
        }

        String san = (String) metadata.get(CertificateEjbcaServiceImpl.META_SAN);
        if (StringUtils.isNotBlank(san)) {
            user.setSubjectAltName(san);
        }

        String extension = (String) metadata.get(CertificateEjbcaServiceImpl.META_EXTENSION);
        setUserExtensions(user, extension);
    }

    private void setUserProfiles(UserDataVOWS user, List<RequestAttributeDto> raProfileAttrs) {
        //String tokenType = AttributeDefinitionUtils.getAttributeValue(ATTRIBUTE_TOKEN_TYPE, raProfileAttrs);
        //user.setTokenType(tokenType);
        user.setTokenType("USERGENERATED");

        Map<Integer, String> endEntityProfile = AttributeDefinitionUtils.getAttributeValue(ATTRIBUTE_END_ENTITY_PROFILE, raProfileAttrs);
        user.setEndEntityProfileName(endEntityProfile.get("name"));

        Map<Integer, String> certificateProfile = AttributeDefinitionUtils.getAttributeValue(ATTRIBUTE_CERTIFICATE_PROFILE, raProfileAttrs);
        user.setCertificateProfileName(certificateProfile.get("name"));

        Map<Integer, String> ca = AttributeDefinitionUtils.getAttributeValue(ATTRIBUTE_CERTIFICATION_AUTHORITY, raProfileAttrs);
        user.setCaName(ca.get("name"));

        boolean sendNotifications = false;
        Object value = AttributeDefinitionUtils.getAttributeValue(ATTRIBUTE_SEND_NOTIFICATIONS, raProfileAttrs);
        if (value != null) {
            sendNotifications = (boolean) value;
        }
        user.setSendNotification(sendNotifications);

        boolean keyRecoverable = false;
        value = AttributeDefinitionUtils.getAttributeValue(ATTRIBUTE_KEY_RECOVERABLE, raProfileAttrs);
        if (value != null) {
            keyRecoverable = (boolean) value;
        }
        user.setKeyRecoverable(keyRecoverable);
    }

    private void setUserExtensions(UserDataVOWS user, String extension) {
        if (StringUtils.isNotBlank(extension)) {
            List<ExtendedInformationWS> ei = new ArrayList<>();
            String[] extensions = extension.split(",[ ]*"); // remove spaces after the comma
            for (String data : extensions) {
                String[] extValue = data.split("=", 2); // split the string using = to 2 values
                // TODO: validation of the data
                ExtendedInformationWS eiWs = new ExtendedInformationWS();
                eiWs.setName(extValue[0]);
                eiWs.setValue(extValue[1]);
                ei.add(eiWs);
            }
            user.getExtendedInformation().addAll(ei);
        }
    }
}
