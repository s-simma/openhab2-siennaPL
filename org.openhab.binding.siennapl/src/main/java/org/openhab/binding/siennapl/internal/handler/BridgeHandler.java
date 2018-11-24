/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.siennapl.internal.handler;

import static org.openhab.binding.internal.siennapl.BindingConstants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.internal.siennapl.BridgeConfiguration;
import org.openhab.binding.internal.siennapl.DeviceConfiguration;
import org.openhab.binding.siennapl.internal.gateway.SerialConnector;
import org.openhab.binding.siennapl.internal.gateway.SerialPacketReader;
import org.openhab.binding.siennapl.internal.packet.ExplicitPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The bridge handler for the Sienna Powerline bridge with RS232 Serial interface.
 * First the Sienna Powerline Sytem has to be configured with the Sienna configuration software
 *
 * Things-config Example:
 * Bridge siennapl:BRIDGE:1 [ serialPort="/dev/ttyUSB0", baud=9600, neuronId="0503E1D2E200", pollingInterval=60 ] {
 * SAM1L A-1 [neuronId="0502601CF600", groupId="A", elementId=1]
 * SAMDR A-2 [neuronId="05024ED65200", groupId="A", elementId=2]
 * SAM2 F-1 [neuronId="05025FC50E60", groupId="F", elementId=1, spinTime=1200]
 * RFGS-CONTACT C-1 [neuronId="0503E8AA7F00", groupId="C", elementId=1, enoceanId="019555F5", enoceanEEP="D5-00-01"]
 * RFGS-MOTION D-1 [neuronId="0503E8AA7F00", groupId="D", elementId=1, enoceanId="018987A3", enoceanEEP="A5-07-01"]
 * RFGS-TEMP D-3 [neuronId="0503E8AA7F00", groupId="D", elementId=3, enoceanId="01959395", enoceanEEP="A5-02-05"]
 * }
 *
 * Items-config Example:
 * Switch Light_SF_Staircase <light> {channel="siennapl:SAM1L:1:A-1:outlet", autoupdate="false"}
 *
 * @author s.simma - Initial Contribution
 */

public class BridgeHandler extends BaseBridgeHandler {

    private Logger logger = LoggerFactory.getLogger(BridgeHandler.class);

    private Bridge bridge;
    private String serialPortName;
    private int baudRate;
    private String neuronId;

    private SerialConnector connector;
    private SerialPacketReader spReader;

    // Monitor task variables
    private long refreshInterval = 0; // default not activated

    // List contains the parameters of all devices
    private List<DeviceConfiguration> deviceParamList = new ArrayList<DeviceConfiguration>();
    int pDeviceParamList = 0;

    private boolean discoveryRunning = false;
    private boolean monitorRunning = false;

    private ScheduledFuture<?> pollingTask;

    private Runnable pollingRunnable = new Runnable() {
        @Override
        public void run() {
            polling();
        }
    };
    // End Monitor task variables

    /**
     * Constructor
     *
     * @param bridge
     */
    public BridgeHandler(Bridge bridge) {
        super(bridge);
        this.bridge = bridge;
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.OFFLINE);
        logger.info("Sienna: Initializing Bridge handler");

        BridgeConfiguration configuration = getConfigAs(BridgeConfiguration.class);
        serialPortName = configuration.serialPort;
        baudRate = configuration.baud;

        refreshInterval = configuration.pollingInterval;
        if ((refreshInterval > 0) && (refreshInterval < 60)) {
            refreshInterval = 2;
        }
        neuronId = configuration.neuronId;
        if (neuronId.length() != NEURONID_LENGHT) {
            neuronId = "000000000000";
            logger.error("Sienna: Neuron-Id of the gateway doesn't have 12 characters -> 000000000000 will be used");
        }

        if (serialPortName != null) {
            /* Start serial communication */
            connector = new SerialConnector(bridge, serialPortName, baudRate, neuronId);
            connector.connect();

            if (connector.isConnected()) {
                // Startup of the binding runs in an own thread (Thread will be closed after init finished)
                scheduler.schedule(delayStartRunnable, 1, TimeUnit.SECONDS);
            } else {
                logger.error("Sienna: Connection to Bridge not possible");
                logger.error("Sienna: Serial Port: {},", serialPortName);
                logger.error("Sienna: Baud:        {},", baudRate);

                updateStatus(ThingStatus.OFFLINE);
                setThingsOffline();
            }
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Sienna: update {} with {}", channelUID.getAsString(), command.toString());
    }

    @Override
    public void dispose() {
        try {
            stopPolling();
            spReader.stopReader();
            connector.disconnect();
        } catch (Exception e) {
            logger.error("Sienna: dispose(): Exception");
        }

        try {
            Thread.sleep(5000);
        } catch (Exception e) {
        }
        logger.debug("Sienna: Gateway disposed");
        super.dispose();
    }

    // Wait for all device handlers to be initialized (Runs only 1 time)
    // Get actual Status of all devices after startup
    // Start polling task for test devices online
    private Runnable delayStartRunnable = new Runnable() {
        @Override
        public void run() {
            // Wait for all Device handlers to be initialized
            boolean allInit = false;
            while (!allInit) {
                allInit = true;
                List<Thing> things = getThing().getThings();
                for (Thing thing : things) {
                    if (thing.getHandler() == null) {
                        try {
                            Thread.sleep(3000);
                        } catch (Exception e) {
                        }
                        allInit = false;
                        break;
                    }
                }
            }

            // Set all Things to OFFLINE
            setThingsOffline();

            // Set Bridge ONLINE
            updateStatus(ThingStatus.ONLINE);

            // Create new Device List by use of configured devices
            List<Thing> things = getThing().getThings();
            deviceParamList.clear();
            for (Thing thing : things) {
                DeviceHandler thingHandler = (DeviceHandler) thing.getHandler();
                if (thingHandler == null) {
                    logger.error("Sienna: Thing handler is null -> skip device");
                    continue;
                }

                // Get device configuration for this device
                DeviceConfiguration configuration = thingHandler.getDeviceConfiguration();

                if (configuration.neuronId.length() != NEURONID_LENGHT) {
                    logger.error("Sienna: Invalid Neuron-Id in your configuration: {}", configuration.neuronId);
                    continue;
                }

                if (configuration.groupId.length() != 1) {
                    logger.error("Sienna: Invalid Group-Id in your configuration: {}", configuration.groupId);
                    continue;
                }
                if (configuration.elementId == 0) {
                    logger.error("Sienna: Invalid Element-Id in your configuration: {}", configuration.elementId);
                    continue;
                }

                DeviceConfiguration devpar = new DeviceConfiguration();

                devpar.neuronId = configuration.neuronId;
                devpar.enoceanId = configuration.enoceanId;
                devpar.enoceanEEP = configuration.enoceanEEP;
                devpar.groupId = configuration.groupId;
                devpar.elementId = configuration.elementId;
                devpar.hwCode = configuration.hwCode;
                devpar.swCode = configuration.swCode;
                devpar.runTime = configuration.runTime;
                devpar.blindActPos = -1;
                devpar.isOnline = 1;

                // Add the device to the binding device list
                deviceParamList.add(devpar);
            }

            /* Start Packet Reader */
            spReader = new SerialPacketReader(connector);
            spReader.start();

            // Send HELLO to Gateway
            helloBridge();
            try {
                Thread.sleep(2000);
            } catch (Exception e) {
            }

            // Send DEVICE_HELLO to all configured devices -> update device status for all devices after startup
            discoveryRunning = false;
            int lenList = deviceParamList.size();
            for (int i = 0; i < lenList; i++) {
                DeviceConfiguration thisdev = getDevParameter(i);

                // Set Pointer to device parameter list to wait for
                setPointDevParList(i);

                // Send DEVICE_HELLO
                requestDevStatus(thisdev.neuronId);

                // Wait for Answer HELLO_EXTEND_RESPONSE or Timeout
                try {
                    int waitCounter = 0;
                    while (true) {
                        Thread.sleep(500);
                        // Did we receive an HELLO_EXTEND_RESPONSE
                        if (getPointDevParList() == -1) {
                            // Answer received -> Goto next
                            break;
                        } else {
                            waitCounter = waitCounter + 1;
                            if (waitCounter >= 10) {
                                // No Answer in 5sec -> Goto next
                                logger.warn("Sienna: No respond from NeuronId: {}", thisdev.neuronId);
                                break;
                            } else {
                                continue;
                            }
                        }
                    }
                } catch (Exception e) {
                }
            }

            logger.info("Sienna Powerline: --------------------------");
            logger.info("Sienna Powerline: Startup procedure COMPLETE");

            /* Start monitor task */
            startPolling();
        }
    };

    // Inform things of bridge status change
    private void setThingsOffline() {
        List<Thing> things = getThing().getThings();

        for (Thing thing : things) {
            DeviceHandler thingHandler = (DeviceHandler) thing.getHandler();

            if (thingHandler != null) {
                thingHandler.setThingOffline();
            }
        }
    }

    //
    // Public Methods
    //

    // Get Bridge configuration
    public BridgeConfiguration getBridgeConfig() {
        return getConfigAs(BridgeConfiguration.class);
    }

    // Provide connector to the gateway
    public SerialConnector getConnector() {
        return this.connector;
    }

    public void helloBridge() {
        if (connector.isConnected()) {
            ExplicitPacket expPacket = new ExplicitPacket(connector);

            expPacket.setCommand(HELLO);
            expPacket.setNeuronId(neuronId);
            expPacket.setGByte((byte) 0x0);
            expPacket.setEByte((byte) 0x0);
            expPacket.setState2((byte) 0x0);
            expPacket.setAnswerCmd((byte) 0x00);
            expPacket.setAnswerElementOffs((byte) 0x00);
            expPacket.sendToBridge(connector);
            logger.info("Sienna: Send HELLO_BRIDGE");
        }
    }

    // Send DEVICE_HELLO for Neuron-Id
    public void requestDevStatus(String neuronId) {
        if (connector.isConnected() && !discoveryRunning) {
            ExplicitPacket expPacket = new ExplicitPacket(connector);
            expPacket.setCommand(DEVICE_HELLO);
            expPacket.setNeuronId(neuronId);
            expPacket.setGByte((byte) 0x0);
            expPacket.setEByte((byte) 0x0);
            expPacket.setState2((byte) 0x0);
            expPacket.setAnswerCmd((byte) 0x00);
            expPacket.setAnswerElementOffs((byte) 0x00);
            expPacket.sendToBridge(connector);
            logger.info("Sienna: Send DEVICE_HELLO for NeuronId: {}", neuronId);
        }
    }

    public boolean isMonitorRunning() {
        return monitorRunning;
    }

    public void resMonitorRunning() {
        monitorRunning = false;
    }

    public void setMonitorRunning() {
        monitorRunning = true;
    }

    public boolean isDiscoveryRunning() {
        return discoveryRunning;
    }

    public void resDiscoveryRunning() {
        discoveryRunning = false;
    }

    public void setDiscoveryRunning() {
        discoveryRunning = true;
    }

    public int getSizeDevParList() {
        return deviceParamList.size();
    }

    public int getPointDevParList() {
        return pDeviceParamList;
    }

    public void setPointDevParList(int value) {
        pDeviceParamList = value;
    }

    public void updatePLDeviceParamList(DeviceConfiguration thisdev) {
        boolean makeNew = true;
        for (DeviceConfiguration devpar : deviceParamList) {
            if (devpar.neuronId.equals(thisdev.neuronId)) {
                if ((devpar.groupId == null) || (devpar.groupId.length() == 0)) {
                    devpar.elementId = thisdev.elementId;
                    devpar.groupId = thisdev.groupId;
                    devpar.hwCode = thisdev.hwCode;
                    devpar.swCode = thisdev.swCode;
                    devpar.runTime = thisdev.runTime;
                    devpar.enoceanEEP = thisdev.enoceanEEP;
                    devpar.enoceanId = thisdev.enoceanId;
                    devpar.blindActPos = thisdev.blindActPos;
                    devpar.isOnline = 0;
                }
                makeNew = false;
                break;
            }
        }
        if (makeNew) {
            deviceParamList.add(thisdev);
        }
    }

    public void updateENODeviceParamList(DeviceConfiguration thisdev) {
        boolean makeNew = true;
        for (DeviceConfiguration devpar : deviceParamList) {
            try {
                if (devpar.enoceanId.equals(thisdev.enoceanId) && devpar.groupId.equals(thisdev.groupId)
                        && (devpar.elementId == thisdev.elementId) && devpar.enoceanEEP.equals(thisdev.enoceanEEP)) {
                    devpar.neuronId = thisdev.neuronId;
                    devpar.hwCode = thisdev.hwCode;
                    makeNew = false;
                    break;
                }
            } catch (Exception e) {
            }
        }
        if (makeNew) {
            deviceParamList.add(thisdev);
        }
    }

    public DeviceConfiguration getDevParameter(int index) {
        DeviceConfiguration thisdev = deviceParamList.get(index);
        return thisdev;
    }

    // Set Device Online in device parameter list
    public void setDeviceOnline(String groupId, int elementId) {
        for (DeviceConfiguration devpar : deviceParamList) {
            try {
                if (devpar.groupId.equals(groupId) && (devpar.elementId == elementId)) {
                    devpar.isOnline = 1;
                    break;
                }
            } catch (Exception e) {
            }
        }
    }

    // Set first received command after send record (only for nv-commands called)
    public void setDeviceAnswCommand(String groupId, int elementId, int elementOffset, byte command) {
        for (DeviceConfiguration devpar : deviceParamList) {
            try {
                if (devpar.groupId.equals(groupId) && (devpar.elementId == elementId)) {
                    switch (elementOffset) {
                        case 0:
                            if (devpar.answerCommand1 == 0) {
                                devpar.answerCommand1 = command;
                            }
                            break;
                        case 1:
                            if (devpar.answerCommand2 == 0) {
                                devpar.answerCommand2 = command;
                            }
                            break;
                    }
                    break;
                }
            } catch (Exception e) {
            }
        }
    }

    // Reset Answer command from device
    public void resDeviceAnswCommand(String groupId, int elementId, int elementOffset) {
        for (DeviceConfiguration devpar : deviceParamList) {
            try {
                if (devpar.groupId.equals(groupId) && (devpar.elementId == elementId)) {
                    switch (elementOffset) {
                        case 0:
                            devpar.answerCommand1 = 0;
                            break;
                        case 1:
                            devpar.answerCommand2 = 0;
                            break;
                    }
                    break;
                }
            } catch (Exception e) {
            }
        }
    }

    // Get last received command for device
    public byte getDeviceAnswCommand(String groupId, int elementId, int elementOffset) {
        byte lcmd = 0;
        for (DeviceConfiguration devpar : deviceParamList) {
            try {
                if (devpar.groupId.equals(groupId) && (devpar.elementId == elementId)) {
                    switch (elementOffset) {
                        case 0:
                            lcmd = devpar.answerCommand1;
                            break;
                        case 1:
                            lcmd = devpar.answerCommand2;
                            break;
                    }
                    break;
                }
            } catch (Exception e) {
            }
        }
        return (lcmd);
    }

    // Save Blind actual position in device parameter list
    public void saveBlindActPos(String groupId, int elementId, int pos) {
        for (DeviceConfiguration devpar : deviceParamList) {
            try {
                if (devpar.groupId.equals(groupId) && (devpar.elementId == elementId)) {
                    devpar.blindActPos = pos;
                    break;
                }
            } catch (Exception e) {
            }
        }
    }

    // Get Blind last position in device parameter list
    public int getBlindActPos(String groupId, int elementId) {
        int lPos = 0;
        for (DeviceConfiguration devpar : deviceParamList) {
            try {
                if (devpar.groupId.equals(groupId) && (devpar.elementId == elementId)) {
                    lPos = devpar.blindActPos;
                    break;
                }
            } catch (Exception e) {
            }
        }
        return (lPos);
    }

    // Save Blind angle actual position in device parameter list
    public void saveBlindAngleActPos(String groupId, int elementId, int pos) {
        for (DeviceConfiguration devpar : deviceParamList) {
            try {
                if (devpar.groupId.equals(groupId) && (devpar.elementId == elementId)) {
                    devpar.angleActPos = pos;
                    break;
                }
            } catch (Exception e) {
            }
        }
    }

    // Get Blind angle actual position in device parameter list
    public int getBlindAngleActPos(String groupId, int elementId) {
        int lPos = 0;
        for (DeviceConfiguration devpar : deviceParamList) {
            try {
                if (devpar.groupId.equals(groupId) && (devpar.elementId == elementId)) {
                    lPos = devpar.angleActPos;
                    break;
                }
            } catch (Exception e) {
            }
        }
        return (lPos);
    }

    // Determine Device Type from Hardware/Software code of the device
    // Module codes can be found in Sienna documentation
    public String getModuleTypeFromCode(int hardwareCode, int softwareCode) {
        String moduleType = "Unknown SiennaPL";

        switch (hardwareCode) {
            case 0:
                moduleType = "NULL";
                break;
            case 65:
                moduleType = DEVICE_AM1;
                break;
            case 66:
                moduleType = DEVICE_AM1;
                break;
            case 67:
                if (softwareCode == 2) {
                    moduleType = DEVICE_AM2L;
                } else if ((softwareCode == 6) || (softwareCode == 7)) {
                    moduleType = DEVICE_AM2;
                } else {
                    moduleType = "Unknown SiennaPL";
                }
                break;
            case 69:
                if (softwareCode == 6) {
                    moduleType = DEVICE_AM2X;
                } else if ((softwareCode == 5) || (softwareCode == 7)) {
                    moduleType = DEVICE_AMX2;
                } else {
                    moduleType = "Unknown SiennaPL";
                }
                break;
            case 3:
                if (softwareCode == 5) {
                    moduleType = DEVICE_SM2;
                } else {
                    moduleType = "Unknown SiennaPL";
                }
                break;
            case 4:
                if (softwareCode == 2) {
                    moduleType = DEVICE_SM4;
                } else if (softwareCode == 1) {
                    moduleType = DEVICE_SM8;
                } else {
                    moduleType = "Unknown SiennaPL";
                }
                break;
            case 33:
                moduleType = "RFGS";
                break;
            case 97:
                if (softwareCode == 1) {
                    moduleType = DEVICE_SAM1L;
                } else if (softwareCode == 3) {
                    moduleType = DEVICE_SAM1LT;
                } else if (softwareCode == 4) {
                    moduleType = DEVICE_SAM1LT;
                } else {
                    moduleType = "Unknown SiennaPL";
                }
                break;
            case 99:
                if (softwareCode == 2) {
                    moduleType = DEVICE_SAM2;
                } else if (softwareCode == 4) {
                    moduleType = DEVICE_SAM2;
                } else if (softwareCode == 3) {
                    moduleType = DEVICE_SAM2L;
                } else {
                    moduleType = "Unknown SiennaPL";
                }
                break;
            case 100:
                if (softwareCode == 2) {
                    moduleType = DEVICE_SAMDR;
                } else if (softwareCode == 3) {
                    moduleType = DEVICE_SAMDR;
                } else if (softwareCode == 4) {
                    moduleType = DEVICE_SAMDU;
                } else {
                    moduleType = "Unknown SiennaPL";
                }
                break;
            default:
                moduleType = "Unknown SiennaPL";
                break;
        }
        return moduleType;
    }

    // Determine Device Type from Hardware/Software code of the device
    // Module codes can be found in Sienna documentation
    public String getModuleTypeFromEEP(String eep) {
        String moduleType = "Unknown SiennaPLENO";

        if (eep.equals("D5-00-01")) {
            moduleType = "RFGS-CONTACT";
        } else if (eep.equals("A5-07-01")) {
            moduleType = "RFGS-MOTION";
        } else if (eep.equals("A5-02-05")) {
            moduleType = "RFGS-TEMP";
        }
        return moduleType;
    }

    /* **************** Start Handling Polling ************************** */
    /**
     * Method to start the polling task.
     */
    public void startPolling() {
        logger.debug("Start SiennaPL Monitor Task");
        if (pollingTask == null || pollingTask.isCancelled()) {
            // If refresh intervall = 0 -> no montior task
            if (refreshInterval > 0) {
                pollingTask = scheduler.scheduleWithFixedDelay(pollingRunnable, refreshInterval, refreshInterval,
                        TimeUnit.MINUTES);
            } else {
                logger.warn("Sienna: Monitor device task not activated Time=0");
            }
        }
    }

    /**
     * Method to stop the polling task.
     */
    public void stopPolling() {
        logger.debug("Sienna: Stopping Monitoring Task..................");
        if ((pollingTask != null) && (!pollingTask.isCancelled())) {
            pollingTask.cancel(true);
            pollingTask = null;
        }
    }

    /**
     * Monitor if Sienna Powerline and ENOCEAN sensors are responing (min time is set to 1 hour)
     */
    public synchronized void polling() {
        if (isMonitorRunning()) {
            logger.warn("Sienna Monitor: Last montior execution is not finished -> increase monitoring time");
            return;
        } else if (isDiscoveryRunning()) {
            return;
        } else if (!connector.isConnected()) {
            logger.error("Sienna Monitor: Not Connected to the gateway -> Reconnect");
            connector.connect();
        }

        setMonitorRunning();
        logger.info("Sienna Monitor: Execute Monitor Task after {} minutes", refreshInterval);

        // Go through all devices
        for (DeviceConfiguration thisdev : deviceParamList) {
            try {
                // Test if device has received response within monitor time
                if (thisdev.isOnline > 0) {
                    thisdev.isOnline = 0;
                    continue;
                }

                // No response from device within monitor time
                String groupId = thisdev.groupId;
                int elementId = thisdev.elementId;

                List<Thing> things = getThing().getThings();

                // Find thing for this device
                for (Thing thing : things) {

                    if (SUPPORTED_DEVICE_THING_TYPES_UIDS.contains(thing.getThingTypeUID())) {
                        DeviceHandler thingHandler = (DeviceHandler) thing.getHandler();
                        if (thingHandler == null) {
                            logger.error("Sienna Monitor: Thing handler is null -> skip device");
                            continue;
                        }
                        // Get device configuration for this device
                        DeviceConfiguration configuration = thingHandler.getDeviceConfiguration();
                        // Test Group/Element is for this device

                        if (configuration.groupId.equals(groupId) && (configuration.elementId == elementId)) {
                            // Thing found -> Is device SiennaPL device ..
                            if ((thisdev.enoceanEEP == null) || (thisdev.enoceanEEP.length() == 0)) {
                                // Did we already try
                                if (thisdev.isOnline == -1) {
                                    // yes
                                    thingHandler.setThingOffline();
                                    thisdev.isOnline = 0;
                                    String thisThing = thing.getThingTypeUID().getId();

                                    // ------------------ Handle AM1, SAM1L, SAM1LT
                                    if (thisThing.equals(DEVICE_AM1) || thisThing.equals(DEVICE_SAM1L)
                                            || thisThing.equals(DEVICE_SAM1LT)) {
                                        thingHandler.onDevice1OnOffChanged(DEVICE_UNDEF, groupId.charAt(0), elementId);

                                        // --------------------- Handle AM2L, AM2X, SAM2L
                                    } else if (thisThing.equals(DEVICE_AM2L) || thisThing.equals(DEVICE_AM2X)
                                            || thisThing.equals(DEVICE_SAM2L)) {
                                        thingHandler.onDeviceNchChanged(DEVICE_UNDEF, groupId.charAt(0),
                                                configuration.elementId, 0);

                                        // --------------------- Handle SM2,SM4,SM8
                                    } else if (thisThing.equals(DEVICE_SM2) || thisThing.equals(DEVICE_SM4)
                                            || thisThing.equals(DEVICE_SM8)) {
                                        thingHandler.onDeviceNchChanged(DEVICE_UNDEF, groupId.charAt(0),
                                                configuration.elementId, 0);

                                        // --------------------- Handle SAMDR, SAMDU (Dimmer)
                                    } else if (thisThing.equals(DEVICE_SAMDR) || thisThing.equals(DEVICE_SAMDU)) {
                                        thingHandler.onDeviceDimmerChanged(DEVICE_UNDEF, groupId.charAt(0), elementId,
                                                0);

                                        // --------------------- Handle SAM2 (Rollershutter with inputs)
                                    } else if (thisThing.equals(DEVICE_SAM2)) {
                                        thingHandler.onDeviceUpDownChanged(DEVICE_UNDEF, groupId.charAt(0), elementId,
                                                0);

                                        // --------------------- Handle AM2, AMX2 (Rollershutter without inputs)
                                    } else if (thisThing.equals(DEVICE_AM2) || thisThing.equals(DEVICE_AMX2)) {
                                        thingHandler.onDeviceUpDownChanged(DEVICE_UNDEF, groupId.charAt(0), elementId,
                                                0);
                                    }

                                    logger.warn(
                                            "Sienna Monitor: No response from sienna powerline device within monitor time: {} g:{} e:{}",
                                            thisdev.neuronId, thisdev.groupId, thisdev.elementId);
                                } else {
                                    thisdev.isOnline = -1;

                                    // Send DEVICE_HALLO to get actual status
                                    requestDevStatus(thisdev.neuronId);
                                }
                            } else if (thisdev.aliveMsg == 1) {
                                // Device is Sienna ENOCEAN sensor -> Set Device is Offline
                                thingHandler.setThingOffline();

                                String thisThing = thing.getThingTypeUID().getId();
                                if (thisThing.equals(DEVICE_RFGS_CONTACT)) {
                                    thingHandler.onDeviceEnoContactChanged(DEVICE_UNDEF, groupId.charAt(0), elementId);
                                } else if (thisThing.equals(DEVICE_RFGS_MOTION)) {
                                    thingHandler.onDeviceEnoMotionChanged(DEVICE_UNDEF, groupId.charAt(0), elementId);
                                } else if (thisThing.equals(DEVICE_RFGS_TEMP)) {
                                    thingHandler.onDeviceEnoTempChanged(DEVICE_UNDEF, groupId.charAt(0), elementId, 0);
                                }

                                thisdev.isOnline = 0;
                                logger.warn(
                                        "Sienna Monitor: No data from ENOCEN device within monitor time: {} g:{} e:{}",
                                        thisdev.neuronId, thisdev.groupId, thisdev.elementId);
                            }
                            // Wait 5sec -> not to lock openhab to much....?
                            try {
                                Thread.sleep(5000);
                            } catch (Exception e) {
                            }
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Sienna Monitor: Unknown Error in monitor task");
            }
        }

        resMonitorRunning();
    }
    /* *************** End Handling monitor devies *********************** */

}
