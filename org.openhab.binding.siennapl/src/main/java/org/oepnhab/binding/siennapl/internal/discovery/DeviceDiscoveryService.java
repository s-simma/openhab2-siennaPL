/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.oepnhab.binding.siennapl.internal.discovery;

import static org.openhab.binding.internal.siennapl.BindingConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.internal.siennapl.BindingConstants;
import org.openhab.binding.internal.siennapl.BridgeConfiguration;
import org.openhab.binding.internal.siennapl.DeviceConfiguration;
import org.openhab.binding.siennapl.internal.gateway.SerialConnector;
import org.openhab.binding.siennapl.internal.handler.BridgeHandler;
import org.openhab.binding.siennapl.internal.packet.ExplicitPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles Device Disovery
 *
 * @author s.simma - initial contribution
 */
public class DeviceDiscoveryService extends AbstractDiscoveryService {

    private Logger logger = LoggerFactory.getLogger(DeviceDiscoveryService.class);
    private static final int SEARCH_TIME = 360;

    private BridgeHandler bridgeHandler;
    private SerialConnector connector;

    public DeviceDiscoveryService(BridgeHandler bridgeHandler) {
        super(BindingConstants.SUPPORTED_DEVICE_THING_TYPES_UIDS, SEARCH_TIME);
        logger.debug("initialize discovery service");
        this.bridgeHandler = bridgeHandler;
    }

    @Override
    public void startScan() {
        connector = bridgeHandler.getConnector();
        if (bridgeHandler.isDiscoveryRunning()) {
            logger.warn("Find SiennaPL devices is already running");
            return;
        } else if (bridgeHandler.isMonitorRunning()) {
            logger.warn("Monitor task is running -> try after monitor task is finished");
            return;
        } else if (!connector.isConnected()) {
            logger.warn("There is no connection to gateway");
            return;
        } else {
            logger.info("Start scanning for Sienna Powerline Devices will start in 10 seconds");

            // Send FIND_FEVICES to the Sienna gateway
            scheduler.schedule(findDevices, 2, TimeUnit.SECONDS);
        }
    }

    @Override
    protected synchronized void stopScan() {
        bridgeHandler.resDiscoveryRunning();
        super.stopScan();
    }

    private Runnable findDevices = new Runnable() {
        @Override
        public void run() {
            bridgeHandler.setDiscoveryRunning();

            connector = bridgeHandler.getConnector();
            ExplicitPacket expPacket = new ExplicitPacket(connector);
            BridgeConfiguration configuration = bridgeHandler.getBridgeConfig();

            bridgeHandler.setPointDevParList(0);
            // Send FIND_DEVICES
            expPacket.setCommand(FIND_DEVICES);
            expPacket.setNeuronId(configuration.neuronId);
            expPacket.setGByte((byte) 0x0);
            expPacket.setEByte((byte) 0x0);
            expPacket.setState2((byte) 0x0);
            expPacket.setAnswerCmd((byte) 0x00);
            expPacket.setAnswerElementOffs((byte) 0x00);
            expPacket.sendToBridge(connector);
            logger.info("Sienna Discovery Service: Send FIND_DEVICES");

            // Wait for FIND_END (Parameter List Pointer = 0) or Timeout
            try {
                int waitCounter = 0;
                while (true) {
                    Thread.sleep(10000);
                    if (bridgeHandler.getPointDevParList() == -1) {
                        // FIND_END received
                        break;
                    } else {
                        waitCounter = waitCounter + 1;
                        if (waitCounter < 12) {
                            continue;
                        } else {
                            // No FIND_END within 20 seconds
                            logger.error("Sienna Discovery Service: FIND_END not received within 120 seconds");
                            break;
                        }
                    }
                }
            } catch (Exception e) {
            }

            // Send DEVICE_HELLO for all configured and new found devices
            if (bridgeHandler.getPointDevParList() == -1) {
                int lenList = bridgeHandler.getSizeDevParList();
                for (int i = 0; i < lenList; i++) {
                    DeviceConfiguration thisdev = bridgeHandler.getDevParameter(i);

                    bridgeHandler.setPointDevParList(i);

                    expPacket.setCommand(DEVICE_HELLO);
                    expPacket.setNeuronId(thisdev.neuronId);
                    expPacket.setGByte((byte) 0x0);
                    expPacket.setEByte((byte) 0x0);
                    expPacket.setState2((byte) 0x0);
                    expPacket.setAnswerCmd((byte) 0x00);
                    expPacket.setAnswerElementOffs((byte) 0x00);
                    expPacket.sendToBridge(connector);
                    logger.info("Sienna Discovery Service: Send DEVICE_HELLO for NeuronId: {}", thisdev.neuronId);

                    // Wait for Answer HELLO_EXTEND_RESPONSE or Timeout
                    try {
                        int waitCounter = 0;
                        while (true) {
                            Thread.sleep(500);
                            if (bridgeHandler.getPointDevParList() == -1) {
                                // Answer received
                                thisdev = bridgeHandler.getDevParameter(i);
                                String moduleType = bridgeHandler.getModuleTypeFromCode(thisdev.hwCode, thisdev.swCode);

                                // Sensor for ENOCEAN gateways will be handled later (Hardware type + Neuron-Id
                                // are saved in device parameter list)
                                if (!moduleType.equals("RFGS")) {
                                    // Add Device to the Inbox
                                    logger.info("Sienna Discovery Service: Add device to Inbox: {}", moduleType);
                                    onDeviceAdded(connector.getBridge(), thisdev, moduleType);
                                }
                                break;
                            } else {
                                waitCounter = waitCounter + 1;
                                if (waitCounter >= 15) {
                                    // No Answer -> Goto next
                                    logger.warn("Sienna Discovery Service: No respond from NeuronId: {}",
                                            thisdev.neuronId);
                                    break;
                                } else {
                                    continue;
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Unknown exception during discovery service");
                    }
                }

                // Now we handle the ENOCEAN Devices for all found gateways
                logger.info("Sienna Discovery Service: Scan for Sienna-Enocean devices ....");

                lenList = bridgeHandler.getSizeDevParList();
                String lastGWNeuronId = "";
                for (int i = 0; i < lenList; i++) {
                    DeviceConfiguration thisdev = bridgeHandler.getDevParameter(i);
                    String moduleType = bridgeHandler.getModuleTypeFromCode(thisdev.hwCode, thisdev.swCode);

                    // We only need each RFGS gateway once
                    if (moduleType.equals("RFGS")) {
                        if (!lastGWNeuronId.equals(thisdev.neuronId)) {
                            lastGWNeuronId = thisdev.neuronId;
                        } else {
                            continue;
                        }
                    } else {
                        continue;
                    }
                    String gwNeuronId = thisdev.neuronId;

                    bridgeHandler.setPointDevParList(i);

                    expPacket.setCommand(GET_CHANNELS);
                    expPacket.setNeuronId(gwNeuronId);
                    expPacket.setGByte((byte) 0x0);
                    expPacket.setEByte((byte) 0x0);
                    expPacket.setState2((byte) 0x0);
                    expPacket.setAnswerCmd((byte) 0x00);
                    expPacket.setAnswerElementOffs((byte) 0x00);
                    expPacket.sendToBridge(connector);
                    logger.info(
                            "Sienna Discovery Service: Send GET_CHANNELS for Sienna Enocean bridge with NeuronId: {}",
                            gwNeuronId);

                    // Wait for ENOCEAN FIND_END (Parameter List Pointer = -1) or Timeout
                    try {
                        int waitCounter = 0;
                        while (true) {
                            Thread.sleep(5000);
                            if (bridgeHandler.getPointDevParList() == -1) {
                                // FIND_END received -> Insert all ENOCEAN sensor for this gateway in INBOX
                                insertIntoInbox(gwNeuronId);
                                break;
                            } else {
                                waitCounter = waitCounter + 1;
                                if (waitCounter >= 2) {
                                    // No ENOCEAN FIND_END within 20 seconds
                                    logger.error(
                                            "Sienna Discovery Service: FIND_END ENOCEAN not received within 10 seconds");
                                    break;
                                } else {
                                    continue;
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Sienna Discovery Service: Exception during GET_CHANNELS from Enocean gateway");
                    }
                }
            }
            bridgeHandler.resDiscoveryRunning();
            logger.info("Scan for SiennaPL/Sienna-Eonocean devices is FINISHED ....");
        }
    };

    // Insert all ENOCEAN sensor which belong to an ENOCEAN gateway into INBOX
    private void insertIntoInbox(String gwNeuronId) {
        int lenList = bridgeHandler.getSizeDevParList();
        for (int i = 0; i < lenList; i++) {
            DeviceConfiguration thisdev = bridgeHandler.getDevParameter(i);
            if (thisdev.neuronId.equals(gwNeuronId)) {
                String moduleType = bridgeHandler.getModuleTypeFromCode(thisdev.hwCode, thisdev.swCode);
                // But not for the gateway itself
                if (moduleType.equals("RFGS")) {
                    continue;
                }
                String enoceanType = bridgeHandler.getModuleTypeFromEEP(thisdev.enoceanEEP);

                logger.info("Sienna Discovery Service: Add device to Inbox: {}", enoceanType);
                onDeviceAdded(connector.getBridge(), thisdev, enoceanType);
            }
        }
    }

    // Add discovered device to the INBOX
    private void onDeviceAdded(Bridge bridge, DeviceConfiguration devconf, String device) {
        logger.debug("Device added to Inbox!");

        ThingTypeUID thingTypeUID = new ThingTypeUID(BINDING_ID, device);
        String deviceId = devconf.groupId + "-" + Integer.toString(devconf.elementId);
        ThingUID thingUID = new ThingUID(thingTypeUID, bridge.getUID(), deviceId);

        Map<String, Object> properties = new HashMap<>(2);
        properties.put(DeviceConfiguration.NEURON_ID, devconf.neuronId);
        properties.put(DeviceConfiguration.ENOCEAN_ID, devconf.enoceanId);
        properties.put(DeviceConfiguration.ENOCEAN_EEP, devconf.enoceanEEP);
        properties.put(DeviceConfiguration.GROUP_ID, devconf.groupId);
        properties.put(DeviceConfiguration.ELEMENT_ID, devconf.elementId);
        properties.put(DeviceConfiguration.HW_CODE, devconf.hwCode);
        properties.put(DeviceConfiguration.SW_CODE, devconf.swCode);

        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                .withLabel(device).build();

        thingDiscovered(discoveryResult);
    }

}
