package com.czertainly.ca.connector.ejbca.api;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.connector.AuthorityInstanceController;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.common.attribute.v2.DataAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.callback.AttributeCallback;
import com.czertainly.api.model.common.attribute.v2.callback.AttributeCallbackMapping;
import com.czertainly.api.model.common.attribute.v2.callback.AttributeValueTarget;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.BooleanAttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.ObjectAttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.connector.authority.AuthorityProviderInstanceDto;
import com.czertainly.api.model.connector.authority.AuthorityProviderInstanceRequestDto;
import com.czertainly.ca.connector.ejbca.service.AuthorityInstanceService;
import com.czertainly.ca.connector.ejbca.service.EndEntityProfileEjbcaService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
public class AuthorityInstanceControllerImpl implements AuthorityInstanceController {

    public static final String ATTRIBUTE_END_ENTITY_PROFILE = "endEntityProfile";
    public static final String ATTRIBUTE_CERTIFICATE_PROFILE = "certificateProfile";
    public static final String ATTRIBUTE_CERTIFICATION_AUTHORITY = "certificationAuthority";
    public static final String ATTRIBUTE_SEND_NOTIFICATIONS = "sendNotifications";
    public static final String ATTRIBUTE_KEY_RECOVERABLE = "keyRecoverable";
    public static final String ATTRIBUTE_USERNAME_GEN_METHOD = "usernameGenMethod";
    public static final String ATTRIBUTE_USERNAME_PREFIX = "usernamePrefix";
    public static final String ATTRIBUTE_USERNAME_POSTFIX = "usernamePostfix";

    public static final String ATTRIBUTE_END_ENTITY_PROFILE_LABEL = "End Entity Profile";
    public static final String ATTRIBUTE_CERTIFICATE_PROFILE_LABEL = "Certificate Profile";
    public static final String ATTRIBUTE_CERTIFICATION_AUTHORITY_LABEL = "Certification Authority";
    public static final String ATTRIBUTE_SEND_NOTIFICATIONS_LABEL = "Send Notifications";
    public static final String ATTRIBUTE_KEY_RECOVERABLE_LABEL = "Key Recoverable";
    public static final String ATTRIBUTE_USERNAME_GEN_METHOD_LABEL = "Username Generation Method";
    public static final String ATTRIBUTE_USERNAME_PREFIX_LABEL = "Username Prefix";
    public static final String ATTRIBUTE_USERNAME_POSTFIX_LABEL = "Username Postfix";
    private AuthorityInstanceService authorityInstanceService;
    private EndEntityProfileEjbcaService endEntityProfileEjbcaService;

    @Autowired
    public void setAuthorityInstanceService(AuthorityInstanceService authorityInstanceService) {
        this.authorityInstanceService = authorityInstanceService;
    }

    @Autowired
    public void setEndEntityProfileEjbcaService(EndEntityProfileEjbcaService endEntityProfileEjbcaService) {
        this.endEntityProfileEjbcaService = endEntityProfileEjbcaService;
    }

    @Override
    public List<AuthorityProviderInstanceDto> listAuthorityInstances() {
        return authorityInstanceService.listAuthorityInstances();
    }

    @Override
    public AuthorityProviderInstanceDto getAuthorityInstance(String uuid) throws NotFoundException {
        return authorityInstanceService.getAuthorityInstance(uuid);
    }

    @Override
    public AuthorityProviderInstanceDto createAuthorityInstance(AuthorityProviderInstanceRequestDto request) throws AlreadyExistException {
        return authorityInstanceService.createAuthorityInstance(request);
    }

    @Override
    public AuthorityProviderInstanceDto updateAuthorityInstance(String uuid, AuthorityProviderInstanceRequestDto request) throws NotFoundException {
        return authorityInstanceService.updateAuthorityInstance(uuid, request);
    }

    @Override
    public void removeAuthorityInstance(String uuid) throws NotFoundException {
        authorityInstanceService.removeAuthorityInstance(uuid);
    }

    @Override
    public void getConnection(String uuid) throws NotFoundException {
        authorityInstanceService.getConnection(uuid);
    }

    @Override
    public List<BaseAttribute> listRAProfileAttributes(String uuid) throws NotFoundException {
        authorityInstanceService.getAuthorityInstance(uuid); // to validate that CA instance exists
        List<BaseAttribute> attrs = new ArrayList<>();

        // transform objects to Attributes that can be selected
        List<BaseAttributeContent> endEntityProfilesContent = new ArrayList<>();
        ArrayList<NameAndIdDto> endEntityProfiles = new ArrayList<>(endEntityProfileEjbcaService.listEndEntityProfiles(uuid));
        for (NameAndIdDto endEntityProfile : endEntityProfiles) {
            ObjectAttributeContent content = new ObjectAttributeContent();
            content.setReference(endEntityProfile.getName());
            content.setData(endEntityProfile);
            endEntityProfilesContent.add(content);
        }

        DataAttribute endEntityProfile = new DataAttribute();
        endEntityProfile.setUuid("baf2d142-f35a-437f-81c7-35c128881fc0");
        endEntityProfile.setName(ATTRIBUTE_END_ENTITY_PROFILE);
        endEntityProfile.setDescription("Available end entity profiles");
        endEntityProfile.setType(AttributeType.DATA);
        endEntityProfile.setContentType(AttributeContentType.OBJECT);
        DataAttributeProperties endEntityProfileProperties = new DataAttributeProperties();
        endEntityProfileProperties.setLabel(ATTRIBUTE_END_ENTITY_PROFILE_LABEL);
        endEntityProfileProperties.setRequired(true);
        endEntityProfileProperties.setReadOnly(false);
        endEntityProfileProperties.setVisible(true);
        endEntityProfileProperties.setList(true);
        endEntityProfileProperties.setMultiSelect(false);
        endEntityProfile.setProperties(endEntityProfileProperties);
        endEntityProfile.setContent(endEntityProfilesContent);
        attrs.add(endEntityProfile);

        Set<AttributeCallbackMapping> mappings = new HashSet<>();
        mappings.add(new AttributeCallbackMapping("authorityId", AttributeValueTarget.PATH_VARIABLE, uuid));
        mappings.add(new AttributeCallbackMapping(ATTRIBUTE_END_ENTITY_PROFILE + ".data.id", "endEntityProfileId", AttributeValueTarget.PATH_VARIABLE));

        DataAttribute certificateProfile = new DataAttribute();
        certificateProfile.setUuid("eb57a756-5a11-4d31-866b-e3f066f7a2b9");
        certificateProfile.setName(ATTRIBUTE_CERTIFICATE_PROFILE);
        certificateProfile.setDescription("Available certificate profiles for selected end entity profile");
        certificateProfile.setType(AttributeType.DATA);
        certificateProfile.setContentType(AttributeContentType.OBJECT);
        DataAttributeProperties certificateProfileProperties = new DataAttributeProperties();
        certificateProfileProperties.setLabel(ATTRIBUTE_CERTIFICATE_PROFILE_LABEL);
        certificateProfileProperties.setRequired(true);
        certificateProfileProperties.setReadOnly(false);
        certificateProfileProperties.setVisible(true);
        certificateProfileProperties.setList(true);
        certificateProfileProperties.setMultiSelect(false);
        certificateProfile.setProperties(certificateProfileProperties);

        AttributeCallback listCertificateProfilesCallback = new AttributeCallback();
        listCertificateProfilesCallback.setCallbackContext("/v1/authorityProvider/authorities/{authorityId}/endEntityProfiles/{endEntityProfileId}/certificateprofiles");
        listCertificateProfilesCallback.setCallbackMethod("GET");
        listCertificateProfilesCallback.setMappings(mappings);
        certificateProfile.setAttributeCallback(listCertificateProfilesCallback);

        attrs.add(certificateProfile);

        DataAttribute certificationAuthority = new DataAttribute();
        certificationAuthority.setUuid("edfd318a-8428-4fd1-b546-fd5238674f78");
        certificationAuthority.setName(ATTRIBUTE_CERTIFICATION_AUTHORITY);
        certificationAuthority.setDescription("Available CAs for selected end entity profile");
        certificationAuthority.setType(AttributeType.DATA);
        certificationAuthority.setContentType(AttributeContentType.OBJECT);
        DataAttributeProperties certificationAuthorityProperties = new DataAttributeProperties();
        certificationAuthorityProperties.setLabel(ATTRIBUTE_CERTIFICATION_AUTHORITY_LABEL);
        certificationAuthorityProperties.setRequired(true);
        certificationAuthorityProperties.setReadOnly(false);
        certificationAuthorityProperties.setVisible(true);
        certificationAuthorityProperties.setList(true);
        certificationAuthorityProperties.setMultiSelect(false);
        certificationAuthority.setProperties(certificationAuthorityProperties);

        AttributeCallback listCAsInProfileCallback = new AttributeCallback();
        listCAsInProfileCallback.setCallbackContext("/v1/authorityProvider/authorities/{authorityId}/endEntityProfiles/{endEntityProfileId}/cas");
        listCAsInProfileCallback.setCallbackMethod("GET");
        listCAsInProfileCallback.setMappings(mappings);
        certificationAuthority.setAttributeCallback(listCAsInProfileCallback);

        attrs.add(certificationAuthority);

        DataAttribute sendNotifications = new DataAttribute();
        sendNotifications.setUuid("e0ab3b4e-7681-4a9f-aec5-e025eb1a56a4");
        sendNotifications.setName(ATTRIBUTE_SEND_NOTIFICATIONS);
        sendNotifications.setDescription("Notifications to be send fot the end entity");
        sendNotifications.setType(AttributeType.DATA);
        sendNotifications.setContentType(AttributeContentType.BOOLEAN);
        DataAttributeProperties sendNotificationsProperties = new DataAttributeProperties();
        sendNotificationsProperties.setLabel(ATTRIBUTE_SEND_NOTIFICATIONS_LABEL);
        sendNotificationsProperties.setRequired(false);
        sendNotificationsProperties.setReadOnly(false);
        sendNotificationsProperties.setVisible(true);
        sendNotificationsProperties.setList(false);
        sendNotificationsProperties.setMultiSelect(false);
        sendNotifications.setProperties(sendNotificationsProperties);
        sendNotifications.setContent(List.of(new BooleanAttributeContent(false)));
        attrs.add(sendNotifications);

        DataAttribute keyRecoverable = new DataAttribute();
        keyRecoverable.setUuid("417077da-bb2b-4f35-a0f7-abf824e345ec");
        keyRecoverable.setName(ATTRIBUTE_KEY_RECOVERABLE);
        keyRecoverable.setDescription("Recovery option for the private key");
        keyRecoverable.setType(AttributeType.DATA);
        keyRecoverable.setContentType(AttributeContentType.BOOLEAN);
        DataAttributeProperties keyRecoverableProperties = new DataAttributeProperties();
        keyRecoverableProperties.setLabel(ATTRIBUTE_KEY_RECOVERABLE_LABEL);
        keyRecoverableProperties.setRequired(false);
        keyRecoverableProperties.setReadOnly(false);
        keyRecoverableProperties.setVisible(true);
        keyRecoverableProperties.setList(false);
        keyRecoverableProperties.setMultiSelect(false);
        keyRecoverable.setProperties(keyRecoverableProperties);
        keyRecoverable.setContent(List.of(new BooleanAttributeContent(false)));
        attrs.add(keyRecoverable);

        // available methods to generate the username in the EJBCA that should be unique
        // there is also option to prefix or postfix the generated username
        ArrayList<String> usernameGenMethods = new ArrayList<>();
        usernameGenMethods.add("RANDOM"); // this will generate random 16 byte Base64 encoded string
        usernameGenMethods.add("CN"); // CN from the request will be used to create username

        List<BaseAttributeContent> usernameGenMethodsContent = new ArrayList<>();
        for (String usernameGenMethod : usernameGenMethods) {
            StringAttributeContent content = new StringAttributeContent();
            content.setData(usernameGenMethod);
            usernameGenMethodsContent.add(content);
        }

        // options to generate the username
        DataAttribute usernameGenMethod = new DataAttribute();
        usernameGenMethod.setUuid("3655e4f5-61d8-49c9-b116-f466a9f8c6b4");
        usernameGenMethod.setDescription("Method to generate username of the end entity");
        usernameGenMethod.setName(ATTRIBUTE_USERNAME_GEN_METHOD);
        usernameGenMethod.setType(AttributeType.DATA);
        usernameGenMethod.setContentType(AttributeContentType.STRING);
        DataAttributeProperties usernameGenMethodProperties = new DataAttributeProperties();
        usernameGenMethodProperties.setLabel(ATTRIBUTE_USERNAME_GEN_METHOD_LABEL);
        usernameGenMethodProperties.setRequired(true);
        usernameGenMethodProperties.setReadOnly(false);
        usernameGenMethodProperties.setVisible(true);
        usernameGenMethodProperties.setList(true);
        usernameGenMethodProperties.setMultiSelect(false);
        usernameGenMethod.setProperties(usernameGenMethodProperties);
        usernameGenMethod.setContent(usernameGenMethodsContent);
        attrs.add(usernameGenMethod);

        // prefix
        DataAttribute usernamePrefix = new DataAttribute();
        usernamePrefix.setUuid("c0c14dee-9319-4b03-af01-6a21bf30c1e3");
        usernamePrefix.setDescription("Optional prefix to be used when generating username");
        usernamePrefix.setName(ATTRIBUTE_USERNAME_PREFIX);
        usernamePrefix.setType(AttributeType.DATA);
        usernamePrefix.setContentType(AttributeContentType.STRING);
        DataAttributeProperties usernamePrefixProperties = new DataAttributeProperties();
        usernamePrefixProperties.setLabel(ATTRIBUTE_USERNAME_PREFIX_LABEL);
        usernamePrefixProperties.setRequired(false);
        usernamePrefixProperties.setReadOnly(false);
        usernamePrefixProperties.setVisible(true);
        usernamePrefixProperties.setList(false);
        usernamePrefixProperties.setMultiSelect(false);
        usernamePrefix.setProperties(usernamePrefixProperties);
        usernamePrefix.setContent(List.of(new StringAttributeContent("czertainly-")));
        attrs.add(usernamePrefix);

        // postfix
        DataAttribute usernamePostfix = new DataAttribute();
        usernamePostfix.setUuid("5c94731f-621e-4851-b40d-b4f4897f0240");
        usernamePostfix.setDescription("Optional postfix to be used when generating username");
        usernamePostfix.setName(ATTRIBUTE_USERNAME_POSTFIX);
        usernamePostfix.setType(AttributeType.DATA);
        usernamePostfix.setContentType(AttributeContentType.STRING);
        DataAttributeProperties usernamePostfixProperties = new DataAttributeProperties();
        usernamePostfixProperties.setLabel(ATTRIBUTE_USERNAME_POSTFIX_LABEL);
        usernamePostfixProperties.setRequired(false);
        usernamePostfixProperties.setReadOnly(false);
        usernamePostfixProperties.setVisible(true);
        usernamePostfixProperties.setList(false);
        usernamePostfixProperties.setMultiSelect(false);
        usernamePostfix.setProperties(usernamePostfixProperties);
        usernamePostfix.setContent(List.of(new StringAttributeContent("-generated")));
        attrs.add(usernamePostfix);

        return attrs;
    }

    @Override
    public void validateRAProfileAttributes(@PathVariable String uuid, @RequestBody List<RequestAttributeDto> attributes) throws ValidationException, NotFoundException {
        AttributeDefinitionUtils.validateAttributes(listRAProfileAttributes(uuid), attributes);
    }
}
