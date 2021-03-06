/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.distribution.agent.impl;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.packaging.Packaging;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.distribution.agent.DistributionAgent;
import org.apache.sling.distribution.component.impl.DistributionComponentUtils;
import org.apache.sling.distribution.component.impl.SettingsUtils;
import org.apache.sling.distribution.event.impl.DistributionEventFactory;
import org.apache.sling.distribution.packaging.DistributionPackageExporter;
import org.apache.sling.distribution.packaging.DistributionPackageImporter;
import org.apache.sling.distribution.packaging.impl.exporter.RemoteDistributionPackageExporter;
import org.apache.sling.distribution.packaging.impl.importer.RemoteDistributionPackageImporter;
import org.apache.sling.distribution.queue.impl.DistributionQueueDispatchingStrategy;
import org.apache.sling.distribution.queue.DistributionQueueProvider;
import org.apache.sling.distribution.queue.impl.SingleQueueDispatchingStrategy;
import org.apache.sling.distribution.queue.impl.jobhandling.JobHandlingDistributionQueueProvider;
import org.apache.sling.distribution.resources.DistributionConstants;
import org.apache.sling.distribution.resources.impl.OsgiUtils;
import org.apache.sling.distribution.serialization.DistributionPackageBuilder;
import org.apache.sling.distribution.serialization.impl.ResourceSharedDistributionPackageBuilder;
import org.apache.sling.distribution.serialization.impl.vlt.FileVaultDistributionPackageBuilder;
import org.apache.sling.distribution.serialization.impl.vlt.JcrVaultDistributionPackageBuilder;
import org.apache.sling.distribution.transport.DistributionTransportSecretProvider;
import org.apache.sling.distribution.transport.impl.TransportEndpointStrategyType;
import org.apache.sling.distribution.trigger.DistributionTrigger;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.settings.SlingSettingsService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An OSGi service factory for {@link org.apache.sling.distribution.agent.DistributionAgent}s which references already existing OSGi services.
 */
@Component(metatype = true,
        label = "Sling Distribution - Sync Agents Factory",
        description = "OSGi configuration factory for syncing agents",
        configurationFactory = true,
        specVersion = "1.1",
        policy = ConfigurationPolicy.REQUIRE
)
@Reference(name = "triggers", referenceInterface = DistributionTrigger.class,
        policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
        bind = "bindDistributionTrigger", unbind = "unbindDistributionTrigger")
public class SyncDistributionAgentFactory {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Property(label = "Name")
    public static final String NAME = DistributionComponentUtils.PN_NAME;

    @Property(boolValue = true, label = "Enabled")
    private static final String ENABLED = "enabled";


    @Property(label = "Service Name")
    public static final String SERVICE_NAME = "serviceName";

    /**
     * endpoints property
     */
    @Property(cardinality = -1)
    public static final String EXPORTER_ENDPOINTS = "packageExporter.endpoints";

    /**
     * endpoints property
     */
    @Property(cardinality = -1)
    public static final String IMPORTER_ENDPOINTS = "packageImporter.endpoints";

    @Reference
    private Packaging packaging;

    @Property(name = "requestAuthorizationStrategy.target")
    @Reference(name = "requestAuthorizationStrategy")
    private DistributionRequestAuthorizationStrategy requestAuthorizationStrategy;


    @Property(name = "transportSecretProvider.target")
    @Reference(name = "transportSecretProvider")
    DistributionTransportSecretProvider transportSecretProvider;


    @Property(name = "packageBuilder.target")
    @Reference(name = "packageBuilder")
    private DistributionPackageBuilder packageBuilder;

    @Reference
    private DistributionEventFactory distributionEventFactory;

    @Reference
    private SlingSettingsService settingsService;

    @Reference
    private JobManager jobManager;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    private ServiceRegistration componentReg;
    private BundleContext savedContext;
    private Map<String, Object> savedConfig;
    private String agentName;
    private final List<DistributionTrigger> triggers = new CopyOnWriteArrayList<DistributionTrigger>();

    private SimpleDistributionAgent agent;

    @Activate
    protected void activate(BundleContext context, Map<String, Object> config) {
        log.info("activating with config {}", OsgiUtils.osgiPropertyMapToString(config));


        savedContext = context;
        savedConfig = config;

        // inject configuration
        Dictionary<String, Object> props = new Hashtable<String, Object>();

        boolean enabled = PropertiesUtil.toBoolean(config.get(ENABLED), true);

        if (enabled) {
            props.put(ENABLED, true);

            agentName = PropertiesUtil.toString(config.get(NAME), null);
            props.put(NAME, agentName);
            props.put(DistributionConstants.PN_IS_RESOURCE, config.get(DistributionConstants.PN_IS_RESOURCE));

            if (componentReg == null) {

                String serviceName = PropertiesUtil.toString(config.get(SERVICE_NAME), null);



                try {


                    String[] exporterEndpoints = PropertiesUtil.toStringArray(config.get(EXPORTER_ENDPOINTS), new String[0]);
                    Map<String, String> importerEndpointsMap = SettingsUtils.toUriMap(config.get(IMPORTER_ENDPOINTS));


                    DistributionPackageExporter packageExporter = new RemoteDistributionPackageExporter(packageBuilder, transportSecretProvider, exporterEndpoints, TransportEndpointStrategyType.All, 1);
                    DistributionPackageImporter packageImporter = new RemoteDistributionPackageImporter(transportSecretProvider, importerEndpointsMap, TransportEndpointStrategyType.All);
                    DistributionQueueProvider queueProvider =  new JobHandlingDistributionQueueProvider(agentName, jobManager, savedContext);
                    DistributionQueueDispatchingStrategy dispatchingStrategy = new SingleQueueDispatchingStrategy();

                    agent = new SimpleDistributionAgent(agentName, false, serviceName,
                            packageImporter, packageExporter, requestAuthorizationStrategy,
                            queueProvider, dispatchingStrategy, distributionEventFactory, resourceResolverFactory, triggers);
                }
                catch (IllegalArgumentException e) {
                    log.warn("cannot create agent", e);
                }

                log.debug("activated agent {}", agentName);

                if (agent != null) {

                    // register agent service
                    componentReg = context.registerService(DistributionAgent.class.getName(), agent, props);
                    agent.enable();
                }
            }
        }
    }

    private void bindDistributionTrigger(DistributionTrigger distributionTrigger, Map<String, Object> config) {
        triggers.add(distributionTrigger);
        if (agent != null) {
            agent.enableTrigger(distributionTrigger);
        }

    }

    private void unbindDistributionTrigger(DistributionTrigger distributionTrigger, Map<String, Object> config) {
        triggers.remove(distributionTrigger);

        if (agent != null) {
            agent.disableTrigger(distributionTrigger);
        }
    }

    @Deactivate
    protected void deactivate(BundleContext context) {
        if (componentReg != null) {
            ServiceReference reference = componentReg.getReference();
            Object service = context.getService(reference);
            if (service instanceof SimpleDistributionAgent) {
                ((SimpleDistributionAgent) service).disable();
            }

            componentReg.unregister();
            componentReg = null;
            agent = null;
        }

    }
}
