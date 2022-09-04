/*
 * Copyright 2012-2014 TORCH GmbH
 *
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.graylog2.periodical;

import com.google.inject.Inject;
import org.graylog2.alarmcallbacks.AlarmCallbackConfiguration;
import org.graylog2.alarmcallbacks.AlarmCallbackConfigurationService;
import org.graylog2.alarmcallbacks.AlarmCallbackFactory;
import org.graylog2.alarmcallbacks.EmailAlarmCallback;
import org.graylog2.alerts.Alert;
import org.graylog2.alerts.AlertService;
import org.graylog2.indexer.Indexer;
import org.graylog2.initializers.IndexerSetupService;
import org.graylog2.plugin.alarms.AlertCondition;
import org.graylog2.plugin.alarms.callbacks.AlarmCallback;
import org.graylog2.plugin.periodical.Periodical;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.shared.ServerStatus;
import org.graylog2.shared.utilities.ExceptionStringFormatter;
import org.graylog2.streams.StreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public class AlertScannerThread extends Periodical {

    private final Logger LOG = LoggerFactory.getLogger(AlertScannerThread.class);
    private AlertService alertService;
    private final StreamService streamService;
    private final AlarmCallbackConfigurationService alarmCallbackConfigurationService;
    private final AlarmCallbackFactory alarmCallbackFactory;
    private final EmailAlarmCallback emailAlarmCallback;
    private final IndexerSetupService indexerSetupService;
    private final Indexer indexer;
    private final ServerStatus serverStatus;

    @Inject
    public AlertScannerThread(AlertService alertService,
                              StreamService streamService,
                              AlarmCallbackConfigurationService alarmCallbackConfigurationService,
                              AlarmCallbackFactory alarmCallbackFactory,
                              EmailAlarmCallback emailAlarmCallback,
                              IndexerSetupService indexerSetupService,
                              Indexer indexer,
                              ServerStatus serverStatus) {
        this.alertService = alertService;
        this.streamService = streamService;
        this.alarmCallbackConfigurationService = alarmCallbackConfigurationService;
        this.alarmCallbackFactory = alarmCallbackFactory;
        this.emailAlarmCallback = emailAlarmCallback;
        this.indexerSetupService = indexerSetupService;
        this.indexer = indexer;
        this.serverStatus = serverStatus;
    }

    @Override
    public void doRun() {
        if (!serverStatus.isProcessing())
            return;


        if (!indexerSetupService.isRunning()) {
            LOG.error("Indexer is not running, not checking streams for alerts.");
            return;
        }
        LOG.debug("Running alert checks.");
        List<Stream> alertedStreams = streamService.loadAllWithConfiguredAlertConditions();

        LOG.debug("There are {} streams with configured alert conditions.", alertedStreams.size());

        // Load all streams that have configured alert conditions.
        for (Stream stream : alertedStreams) {

            LOG.debug("Stream [{}] has [{}] configured alert conditions.", stream, streamService.getAlertConditions(stream).size());

            if(stream.isPaused()) {
                LOG.debug("Stream [{}] has been paused. Skipping alert check.", stream);
                continue;
            }

            // Check if a threshold is reached.
            for (AlertCondition alertCondition : streamService.getAlertConditions(stream)) {
                try {
                    AlertCondition.CheckResult result = alertService.triggered(alertCondition, indexer);
                    if (result.isTriggered()) {
                        // Alert is triggered!
                        LOG.debug("Alert condition [{}] is triggered. Sending alerts.", alertCondition);

                        // Persist alert.
                        Alert alert = alertService.factory(result);
                        alertService.save(alert);

                        List<AlarmCallbackConfiguration> callConfigurations = alarmCallbackConfigurationService.getForStream(stream);
                        if (callConfigurations.size() > 0)
                            for (AlarmCallbackConfiguration configuration : callConfigurations) {
                                AlarmCallback alarmCallback = alarmCallbackFactory.create(configuration);
                                try {
                                    alarmCallback.call(stream, result);
                                } catch (Exception e) {
                                    LOG.warn("Alarm callback <{}> failed. Skipping. Error was: {}", alarmCallback.getName(), new ExceptionStringFormatter(e));
                                }
                            }
                        else
                            emailAlarmCallback.call(stream, result);
                    } else {
                        // Alert not triggered.
                        LOG.debug("Alert condition [{}] is triggered.", alertCondition);
                    }
                } catch(Exception e) {
                    LOG.error("Skipping alert check that threw an exception.", e);
                }
            }

        }
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    @Override
    public boolean runsForever() {
        return false;
    }

    @Override
    public boolean isDaemon() {
        return true;
    }

    @Override
    public boolean stopOnGracefulShutdown() {
        return true;
    }

    @Override
    public boolean masterOnly() {
        return true;
    }

    @Override
    public boolean startOnThisNode() {
        return true;
    }

    @Override
    public int getInitialDelaySeconds() {
        return 10;
    }

    @Override
    public int getPeriodSeconds() {
        return 60;
    }
}
