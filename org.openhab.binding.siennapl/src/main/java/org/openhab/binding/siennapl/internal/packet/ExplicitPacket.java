/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.siennapl.internal.packet;

import static org.openhab.binding.internal.siennapl.BindingConstants.*;

import java.util.List;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.openhab.binding.internal.siennapl.DeviceConfiguration;
import org.openhab.binding.siennapl.internal.gateway.SerialConnector;
import org.openhab.binding.siennapl.internal.handler.BridgeHandler;
import org.openhab.binding.siennapl.internal.handler.DeviceHandler;
import org.openhab.binding.siennapl.internal.utils.ByteArray;
import org.openhab.binding.siennapl.internal.utils.ByteStringConverter;
import org.openhab.binding.siennapl.internal.utils.LonCRC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle Explicit Packages from/to Sienna Bridge
 *
 * @author s.simma - initial contribution
 *
 */
public class ExplicitPacket {

    private Logger logger = LoggerFactory.getLogger(ExplicitPacket.class);

    private BridgeHandler bridgeHandler;
    private SerialConnector connector;

    private byte command;

    /* For explicit type1 */
    private String neuronid;

    /* For explicit type2 */
    private byte hwCode;
    private byte swCode;
    private byte devState;
    private byte neuronId3;
    private byte neuronId4;
    private byte devState1;
    private byte gByte;
    private byte eByte;
    private byte devState2;
    private byte answerCmd;
    private byte answerElementOffs;

    private byte crc;

    public ExplicitPacket(SerialConnector connector) {
        this.connector = connector;
        this.bridgeHandler = (BridgeHandler) connector.getBridge().getHandler();
    }

    // Command
    public void setCommand(byte command) {
        this.command = command;
    }

    // Set NeuronId
    public void setNeuronId(String NeuronId) {
        this.neuronid = NeuronId;
    }

    // Module Type
    public void setModuleCode(byte modcode) {
        this.hwCode = modcode;
    }

    // SW Type
    public void setSoftwCode(byte softwcode) {
        this.swCode = softwcode;
    }

    // State
    public void setState(byte state) {
        this.devState = state;
    }

    // Neuron-ID 3
    public void setNeuron3(byte neuron3) {
        this.neuronId3 = neuron3;
    }

    // Neuron-ID 4
    public void setNeuron4(byte neuron4) {
        this.neuronId4 = neuron4;
    }

    // State1
    public void setState1(byte state1) {
        this.devState1 = state1;
    }

    // G-Byte
    public void setGByte(byte gByte) {
        this.gByte = gByte;
    }

    // E-Byte
    public void setEByte(byte eByte) {
        this.eByte = eByte;
    }

    // State2
    public void setState2(byte state2) {
        this.devState2 = state2;
    }

    // Set expected answer command from device
    public void setAnswerCmd(byte AnswerCmd) {
        this.answerCmd = AnswerCmd;
    }

    // Set expected answer element offset from device
    public void setAnswerElementOffs(byte AnswerElementOffs) {
        this.answerElementOffs = AnswerElementOffs;
    }

    // CRC
    public void setCrc(byte Crc) {
        this.crc = Crc;
    }

    // Test CRC for received package is valid
    public boolean isValid() {
        return unsignedByte(calculateCRC()) == unsignedByte(crc);
    }

    // Send an explicit package to the bridge
    //
    public void sendToBridge(SerialConnector connector) {
        connector.write(createRecord());
    }

    // Handle a received explicit package from the bridge
    //
    public void handleCommand() {
        byte[] thisNeuronId = new byte[NEURONID_LENGHT / 2];
        ByteArray msg = new ByteArray();

        switch (command) {
            case COM_ERROR_SERIAL:
                connector.setSerialError(true);
                logger.warn("Sienna: Received COM_ERROR_SERIAL from bridge");
                break;
            case COM_ERROR_BUS:
                logger.error("Sienna: Received COM_ERROR_BUS from bridge");
                break;
            case DEVICE_FOUND:
                // In this case the received record has only the Neuron-Id of the device
                thisNeuronId[0] = hwCode;
                thisNeuronId[1] = swCode;
                thisNeuronId[2] = devState;
                thisNeuronId[3] = neuronId3;
                thisNeuronId[4] = neuronId4;
                thisNeuronId[5] = devState1;
                msg.addBytes(thisNeuronId);
                logger.info("Sienna: Received DEVICE_FOUND with Neuron-Id: {}", msg.toString());

                // if State = 255 -> Device is not configured
                if (devState != 255) {
                    /*
                     * At this time only the Neuron-Id is available
                     * Other parameter will follow with HELLO_EXTEND_RESPONSE
                     */
                    DeviceConfiguration devpar = new DeviceConfiguration();
                    devpar.neuronId = new ByteStringConverter().byteArrayToString(thisNeuronId);
                    devpar.enoceanId = "";
                    devpar.enoceanEEP = "";
                    devpar.groupId = "";
                    devpar.elementId = 0;
                    devpar.runTime = 0;
                    devpar.hwCode = 0;
                    devpar.swCode = 0;
                    devpar.blindActPos = -1;
                    devpar.isOnline = 1;

                    // Update/Make a new entry in device parameter list
                    bridgeHandler.updatePLDeviceParamList(devpar);
                } else {
                    logger.warn("Sienna: Device with this Neuron-Id is not configured: {}", msg.toString());
                }
                break;
            case DEVICE_END:
                logger.info("Sienna: Received FIND_END -> Scann for available SiennaPL devices is complete");

                if (devState2 == 0) {
                    logger.warn(
                            "Sienna: NO devices found. Check if gateway is configured (has a valid !!! DOMAIN !!!)");
                }

                // Start send DEVICE_HELLO to all devices
                bridgeHandler.setPointDevParList(-1);
                break;
            // Handle the answer for command HELLO_DEVICE
            case HELLO_EXTEND_RESPONSE:
                int devGroup = gByte & 0x0f;
                int devElement = eByte & 0x7f;

                logger.info("Sienna: Received HELLO_EXTEND_RESPONSE: g:{} e:{}", (char) (gByte + 0x40), eByte);
                if (eByte != 0) { // Probably ENOCEAN Gateway
                    Bridge bridge = connector.getBridge();

                    List<Thing> things = bridge.getThings();
                    for (Thing thing : things) {

                        // If it is a Sienna devices
                        if (SUPPORTED_DEVICE_THING_TYPES_UIDS.contains(thing.getThingTypeUID())) {
                            DeviceHandler thingHandler = (DeviceHandler) thing.getHandler();

                            if ((thingHandler != null) && (thing.getHandler() instanceof DeviceHandler)) {
                                // Get device configuration for this device
                                DeviceConfiguration configuration = thingHandler.getDeviceConfiguration();

                                // Test received Group/Element is for this thing
                                if ((configuration.groupId.equals(Character.toString((char) (gByte + 0x40))))
                                        && (configuration.elementId == eByte)) {
                                    String thisThing = thing.getThingTypeUID().getId();

                                    // ---- Handle AM1, SAM1L, SAM1LT ------------------------------------------------
                                    if (thisThing.equals(DEVICE_AM1) || thisThing.equals(DEVICE_SAM1L)
                                            || thisThing.equals(DEVICE_SAM1LT)) {
                                        if ((devState & 0x1) > 0) {
                                            thingHandler.onDevice1OnOffChanged(STATUS_ON, (char) (devGroup + 0x40),
                                                    devElement);
                                        } else {
                                            thingHandler.onDevice1OnOffChanged(STATUS_OFF, (char) (devGroup + 0x40),
                                                    devElement);
                                        }

                                        // ---- Handle AM2X, AM2L, SAM2L -----------------------------------------
                                    } else if (thisThing.equals(DEVICE_AM2L) || thisThing.equals(DEVICE_AM2X)
                                            || thisThing.equals(DEVICE_SAM2L)) {
                                        // Output 1 state
                                        if ((devState & 0x1) > 0) {
                                            thingHandler.onDeviceNchChanged(STATUS_DOWN, (char) (devGroup + 0x40),
                                                    devElement, 0);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STATUS_OFF, (char) (devGroup + 0x40),
                                                    devElement, 0);
                                        }
                                        // Output 2 state
                                        if ((devState & 0x10) > 0) {
                                            thingHandler.onDeviceNchChanged(STATUS_DOWN, (char) (devGroup + 0x40),
                                                    devElement, 1);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STATUS_OFF, (char) (devGroup + 0x40),
                                                    devElement, 1);
                                        }

                                        // ---- Handle SM2 ------------------------------------------------------
                                    } else if (thisThing.equals(DEVICE_SM2)) {
                                        if ((devState1 & 0x1) > 0) {
                                            thingHandler.onDeviceNchChanged(GO, (char) (devGroup + 0x40), devElement,
                                                    0);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STOP_GO, (char) (devGroup + 0x40),
                                                    devElement, 0);
                                        }
                                        if ((devState1 & 0x2) > 0) {
                                            thingHandler.onDeviceNchChanged(GO, (char) (devGroup + 0x40), devElement,
                                                    1);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STOP_GO, (char) (devGroup + 0x40),
                                                    devElement, 1);
                                        }

                                        // ---- Handle SM4 ------------------------------------------------------
                                    } else if (thisThing.equals(DEVICE_SM4)) {
                                        if ((devState1 & 0x1) > 0) {
                                            thingHandler.onDeviceNchChanged(GO, (char) (devGroup + 0x40), devElement,
                                                    0);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STOP_GO, (char) (devGroup + 0x40),
                                                    devElement, 0);
                                        }
                                        if ((devState1 & 0x2) > 0) {
                                            thingHandler.onDeviceNchChanged(GO, (char) (devGroup + 0x40), devElement,
                                                    1);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STOP_GO, (char) (devGroup + 0x40),
                                                    devElement, 1);
                                        }
                                        if ((devState1 & 0x4) > 0) {
                                            thingHandler.onDeviceNchChanged(GO, (char) (devGroup + 0x40), devElement,
                                                    2);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STOP_GO, (char) (devGroup + 0x40),
                                                    devElement, 2);
                                        }
                                        if ((devState1 & 0x8) > 0) {
                                            thingHandler.onDeviceNchChanged(GO, (char) (devGroup + 0x40), devElement,
                                                    3);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STOP_GO, (char) (devGroup + 0x40),
                                                    devElement, 3);
                                        }

                                        // ---- Handle SM8 ------------------------------------------------------
                                    } else if (thisThing.equals(DEVICE_SM8)) {
                                        if ((devState1 & 0x1) > 0) {
                                            thingHandler.onDeviceNchChanged(GO, (char) (devGroup + 0x40), devElement,
                                                    0);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STOP_GO, (char) (devGroup + 0x40),
                                                    devElement, 0);
                                        }
                                        if ((devState1 & 0x2) > 0) {
                                            thingHandler.onDeviceNchChanged(GO, (char) (devGroup + 0x40), devElement,
                                                    1);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STOP_GO, (char) (devGroup + 0x40),
                                                    devElement, 1);
                                        }
                                        if ((devState1 & 0x4) > 0) {
                                            thingHandler.onDeviceNchChanged(GO, (char) (devGroup + 0x40), devElement,
                                                    2);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STOP_GO, (char) (devGroup + 0x40),
                                                    devElement, 2);
                                        }
                                        if ((devState1 & 0x8) > 0) {
                                            thingHandler.onDeviceNchChanged(GO, (char) (devGroup + 0x40), devElement,
                                                    3);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STOP_GO, (char) (devGroup + 0x40),
                                                    devElement, 3);
                                        }
                                        if ((devState1 & 0x10) > 0) {
                                            thingHandler.onDeviceNchChanged(GO, (char) (devGroup + 0x40), devElement,
                                                    4);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STOP_GO, (char) (devGroup + 0x40),
                                                    devElement, 4);
                                        }
                                        if ((devState1 & 0x20) > 0) {
                                            thingHandler.onDeviceNchChanged(GO, (char) (devGroup + 0x40), devElement,
                                                    5);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STOP_GO, (char) (devGroup + 0x40),
                                                    devElement, 5);
                                        }
                                        if ((devState1 & 0x40) > 0) {
                                            thingHandler.onDeviceNchChanged(GO, (char) (devGroup + 0x40), devElement,
                                                    6);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STOP_GO, (char) (devGroup + 0x40),
                                                    devElement, 6);
                                        }
                                        if ((devState1 & 0x80) > 0) {
                                            thingHandler.onDeviceNchChanged(GO, (char) (devGroup + 0x40), devElement,
                                                    7);
                                        } else {
                                            thingHandler.onDeviceNchChanged(STOP_GO, (char) (devGroup + 0x40),
                                                    devElement, 7);
                                        }

                                        // -- Handle SAMDR, SAMDU (Dimmer) --------------------------------------
                                    } else if (thisThing.equals(DEVICE_SAMDR) || thisThing.equals(DEVICE_SAMDU)) {
                                        if ((devState & 0x1) > 0) {
                                            thingHandler.onDevice1OnOffChanged(STATUS_ON, (char) (devGroup + 0x40),
                                                    devElement);
                                        } else {
                                            thingHandler.onDevice1OnOffChanged(STATUS_OFF, (char) (devGroup + 0x40),
                                                    devElement);
                                        }
                                        // Dimm value
                                        double d = (unsignedByte(devState1));
                                        thingHandler.onDeviceDimmerChanged(REPORT_DIM_VALUE, (char) (devGroup + 0x40),
                                                devElement, d);

                                        // --- Handle SAM2 (Rollershutter with inputs) -------------------------------
                                    } else if (thisThing.equals(DEVICE_SAM2)) {
                                        double d = (unsignedByte(devState1));
                                        thingHandler.onDeviceUpDownChanged(REPORT_MOTOR_POS, (char) (devGroup + 0x40),
                                                devElement, d);

                                        // --- Handle AM2, AMX2 (Rollershutter without inputs)
                                        // -------------------------------
                                    } else if (thisThing.equals(DEVICE_AM2) || thisThing.equals(DEVICE_AMX2)) {
                                        double d = (unsignedByte(devState1));
                                        thingHandler.onDeviceUpDownChanged(REPORT_MOTOR_POS, (char) (devGroup + 0x40),
                                                devElement, d);
                                    }

                                    // Set Device is online
                                    bridgeHandler.setDeviceOnline(configuration.groupId, configuration.elementId);

                                    // Set Device is Online
                                    thingHandler.setThingOnline();

                                    break;
                                }
                            }
                        } else {
                            logger.warn("Sienna: Device {} is not supported by the binding",
                                    thing.getThingTypeUID().getId());
                        }
                    }
                }

                // Update module parameter in device parameter list if FIND_DEVICE is running
                if (bridgeHandler.isDiscoveryRunning()) {
                    DeviceConfiguration thisdev = bridgeHandler.getDevParameter(bridgeHandler.getPointDevParList());
                    if ((thisdev.groupId == null) || (thisdev.groupId.length() == 0)) {
                        thisdev.enoceanId = "";
                        thisdev.enoceanEEP = "";
                        thisdev.groupId = Character.toString((char) (gByte + 0x40));
                        thisdev.elementId = eByte;
                        thisdev.hwCode = hwCode;
                        thisdev.swCode = swCode;
                        thisdev.runTime = devState2;
                        thisdev.angleActPos = -1;
                        thisdev.isOnline = 1;
                    }
                }

                // Set HELLO_EXTEND_RESPONSE received
                bridgeHandler.setPointDevParList(-1);
                break;
            case REPORT_RFE_STATE:
                byte[] thisEnoceanId = new byte[4];
                thisEnoceanId[0] = hwCode;
                thisEnoceanId[1] = swCode;
                thisEnoceanId[2] = devState;
                thisEnoceanId[3] = neuronId3;
                msg.addBytes(thisEnoceanId);
                logger.info("Sienna: Received REPORT_RFE_STATE with Enocean-Id: {}", msg.toString());

                // Get the Neuron-Id from the actual gateway (all sensors which belong to this gateway have the same
                // Neuron-Id
                DeviceConfiguration thisdev = bridgeHandler.getDevParameter(bridgeHandler.getPointDevParList());

                DeviceConfiguration devpar = new DeviceConfiguration();
                devpar.neuronId = thisdev.neuronId;
                devpar.enoceanId = new ByteStringConverter().byteArrayToString(thisEnoceanId);
                devpar.hwCode = 0; // In this case nothing
                devpar.swCode = 0;

                if (devState1 == 0x11) {
                    devpar.enoceanEEP = "D5-00-01"; // Contact
                } else if (devState1 == 0x23) {
                    devpar.enoceanEEP = "A5-07-01"; // Motion
                } else if (devState1 == 0x42) {
                    devpar.enoceanEEP = "A5-02-05"; // Temperature
                } else {
                    devpar.enoceanEEP = "Unknown";
                }

                // In this case Group-Id
                devpar.groupId = Character.toString((char) ((unsignedByte(neuronId4) >> 4) + 0x40));
                devpar.elementId = gByte; // In this case Element -Id

                // Update/Make a new entry in device parameter list
                bridgeHandler.updateENODeviceParamList(devpar);
                break;
            case REPORT_RFE_END:
                logger.info(
                        "Sienna: Received REPORT_RFE_END -> Scann for available Sienna-Enocean devices is complete");

                // Start send DEVICE_HELLO to all devices
                bridgeHandler.setPointDevParList(-1);
                break;
            case SERVICE_PIN:
                thisNeuronId[0] = hwCode;
                thisNeuronId[1] = swCode;
                thisNeuronId[2] = devState;
                thisNeuronId[3] = neuronId3;
                thisNeuronId[4] = neuronId4;
                thisNeuronId[5] = devState1;
                msg.addBytes(thisNeuronId);
                logger.info("Sienna: Received SERVICE_PIN from device with Neuron-Id: {}", msg.toString());
                break;
            default:
                break;
        }
    }

    // Create Byte Array record (STX,CMD,Neuron-ID(6),G-Byte,E-Byte,Stat2,CRC,ETX)
    private byte[] createRecord() {
        ByteArray data = new ByteArray();
        data.addByte(command);
        byte[] ba = new ByteStringConverter().stringToByteArray(neuronid);
        data.addBytes(ba);
        data.addByte(gByte);
        data.addByte(eByte);
        data.addByte(devState2);

        // Calculate Lon-CRC Byte
        LonCRC loncrc = new LonCRC();
        byte crc = loncrc.calcLonCRC(data.getArray());

        ByteArray record = new ByteArray();
        record.addByte(START_BYTE);
        record.addBytes(data.getArray());
        record.addByte(crc);
        record.addByte(STOP_BYTE);
        record.addByte(answerCmd);
        record.addByte(answerElementOffs);
        return record.getArray();
    }

    private byte calculateCRC() {
        ByteArray data = new ByteArray();
        data.addByte(command);
        data.addByte(hwCode);
        data.addByte(swCode);
        data.addByte(devState);
        data.addByte(neuronId3);
        data.addByte(neuronId4);
        data.addByte(devState1);
        data.addByte(gByte);
        data.addByte(eByte);
        data.addByte(devState2);

        // Calculate Lon-CRC Byte
        LonCRC loncrc = new LonCRC();
        return loncrc.calcLonCRC(data.getArray());
    }

    private int unsignedByte(byte b) {
        int unsbyte = b;
        if (unsbyte < 0) {
            unsbyte = 128 + (128 - Math.abs(unsbyte));
            return unsbyte;
        } else {
            return unsbyte;
        }
    }
}
