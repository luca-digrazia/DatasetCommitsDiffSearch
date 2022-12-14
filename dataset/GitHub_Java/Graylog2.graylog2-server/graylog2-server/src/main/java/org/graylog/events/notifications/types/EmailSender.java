/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog.events.notifications.types;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.floreysoft.jmte.Engine;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailConstants;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.graylog.events.notifications.EventBacklogService;
import org.graylog.events.notifications.EventNotificationContext;
import org.graylog.events.notifications.EventNotificationModelData;
import org.graylog.events.processor.DBEventDefinitionService;
import org.graylog.events.processor.EventDefinitionDto;
import org.graylog2.alerts.EmailRecipients;
import org.graylog2.configuration.EmailConfiguration;
import org.graylog2.jackson.TypeReferences;
import org.graylog2.notifications.Notification;
import org.graylog2.notifications.NotificationService;
import org.graylog2.plugin.MessageSummary;
import org.graylog2.plugin.alarms.transports.TransportConfigurationException;
import org.graylog2.plugin.system.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;

public class EmailSender {
    private static final Logger LOG = LoggerFactory.getLogger(EmailSender.class);
    private static final String UNKNOWN = "<unknown>";

    private final EmailConfiguration emailConfig;
    private final EmailRecipients.Factory emailRecipientsFactory;
    private final EventBacklogService eventBacklogService;
    private final NotificationService notificationService;
    private final NodeId nodeId;
    private final DBEventDefinitionService eventDefinitionService;
    private final ObjectMapper objectMapper;
    private final Engine templateEngine;

    @Inject
    public EmailSender(EmailConfiguration emailConfig,
                       EmailRecipients.Factory emailRecipientsFactory,
                       EventBacklogService eventBacklogService,
                       NotificationService notificationService,
                       NodeId nodeId,
                       DBEventDefinitionService eventDefinitionService,
                       ObjectMapper objectMapper,
                       Engine templateEngine) {
        this.emailConfig = requireNonNull(emailConfig, "emailConfig");
        this.emailRecipientsFactory = requireNonNull(emailRecipientsFactory, "emailRecipientsFactory");
        this.eventBacklogService = eventBacklogService;
        this.notificationService = requireNonNull(notificationService, "notificationService");
        this.nodeId = requireNonNull(nodeId, "nodeId");
        this.eventDefinitionService = eventDefinitionService;
        this.objectMapper = requireNonNull(objectMapper, "objectMapper)");
        this.templateEngine = requireNonNull(templateEngine, "templateEngine");
    }

    @VisibleForTesting
    private String buildSubject(EmailEventNotificationConfig config, Map<String, Object> model) {
        final String template;
        if (isNullOrEmpty(config.subject())) {
            template = EmailEventNotificationConfig.DEFAULT_SUBJECT;
        } else {
            template = config.subject();
        }

        return templateEngine.transform(template, model);
    }

    @VisibleForTesting
    private String buildBody(EmailEventNotificationConfig config, Map<String, Object> model) {
        final String template;
        if (isNullOrEmpty(config.bodyTemplate())) {
            template = EmailEventNotificationConfig.DEFAULT_BODY_TEMPLATE;
        } else {
            template = config.bodyTemplate();
        }

        return this.templateEngine.transform(template, model);
    }

    private Map<String, Object> getModel(EventNotificationContext ctx, ImmutableList<MessageSummary> backlog) {
        final Optional<EventDefinitionDto> definitionDto = ctx.eventDefinition();

        // TODO: This needs at search URL, event definition URL, anything else?
        final EventNotificationModelData modelData = EventNotificationModelData.builder()
                .eventDefinitionId(definitionDto.map(EventDefinitionDto::id).orElse(UNKNOWN))
                .eventDefinitionType(definitionDto.map(d -> d.config().type()).orElse(UNKNOWN))
                .eventDefinitionTitle(definitionDto.map(EventDefinitionDto::title).orElse(UNKNOWN))
                .eventDefinitionDescription(definitionDto.map(EventDefinitionDto::description).orElse(UNKNOWN))
                .jobDefinitionId(ctx.jobTrigger().jobDefinitionId())
                .jobTriggerId(ctx.jobTrigger().id())
                .event(ctx.event())
                .backlog(backlog)
                .build();

        return objectMapper.convertValue(modelData, TypeReferences.MAP_STRING_OBJECT);
    }

    private void sendEmail(EmailEventNotificationConfig config, String emailAddress, Map<String, Object> model) throws TransportConfigurationException, EmailException {
        LOG.debug("Sending mail to " + emailAddress);
        if (!emailConfig.isEnabled()) {
            throw new TransportConfigurationException("Email transport is not enabled in server configuration file!");
        }

        final Email email = new SimpleEmail();
        email.setCharset(EmailConstants.UTF_8);

        if (isNullOrEmpty(emailConfig.getHostname())) {
            throw new TransportConfigurationException("No hostname configured for email transport while trying to send notification email!");
        } else {
            email.setHostName(emailConfig.getHostname());
        }
        email.setSmtpPort(emailConfig.getPort());

        if (emailConfig.isUseSsl()) {
            email.setSslSmtpPort(Integer.toString(emailConfig.getPort()));
        }

        if (emailConfig.isUseAuth()) {
            email.setAuthenticator(new DefaultAuthenticator(
                    Strings.nullToEmpty(emailConfig.getUsername()),
                    Strings.nullToEmpty(emailConfig.getPassword())
            ));
        }

        if (isNullOrEmpty(config.sender())) {
            email.setFrom(emailConfig.getFromEmail());
        } else {
            email.setFrom(config.sender());
        }

        email.setSSLOnConnect(emailConfig.isUseSsl());
        email.setStartTLSEnabled(emailConfig.isUseTls());
        email.setSubject(buildSubject(config, model));
        email.setMsg(buildBody(config, model));
        email.addTo(emailAddress);

        email.send();
    }

    // TODO: move EmailRecipients class to events code
    void sendEmails(EmailEventNotificationConfig notificationConfig, EventNotificationContext ctx, ImmutableList<MessageSummary> backlog) throws TransportConfigurationException, EmailException, ConfigurationError {
        if (!emailConfig.isEnabled()) {
            throw new TransportConfigurationException("Email transport is not enabled in server configuration file!");
        }

        final EmailRecipients emailRecipients = emailRecipientsFactory.create(
                new ArrayList<>(notificationConfig.userRecipients()),
                new ArrayList<>(notificationConfig.emailRecipients())
        );

        if (!emailConfig.isEnabled()) {
            LOG.debug("Email transport is not enabled in server configuration file!");
            return;
        }

        if (emailRecipients.isEmpty()) {
            LOG.debug("Cannot send emails: empty recipient list.");
            return;
        }

        final Set<String> recipientsSet = emailRecipients.getEmailRecipients();
        if (recipientsSet.size() == 0) {
            final Notification notification = notificationService.buildNow()
                    .addNode(nodeId.toString())
                    .addType(Notification.Type.GENERIC)
                    .addSeverity(Notification.Severity.NORMAL)
                    .addDetail("title", "No recipients have been defined!")
                    .addDetail("description", "To fix this, go to the notification configuration and add at least one alert recipient.");
            notificationService.publishIfFirst(notification);
        }

        final Map<String, Object> model = getModel(ctx, backlog);

        for (String email : recipientsSet) {
            sendEmail(notificationConfig, email, model);
        }
    }

    static class ConfigurationError extends Exception {
        ConfigurationError(String message) {
            super(message);
        }
    }
}
