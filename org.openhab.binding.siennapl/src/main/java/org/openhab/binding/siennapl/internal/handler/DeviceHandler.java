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

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StopMoveType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.library.types.UpDownType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.internal.siennapl.DeviceConfiguration;
import org.openhab.binding.siennapl.internal.gateway.SerialConnector;
import org.openhab.binding.siennapl.internal.packet.NVPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Device handler for the Sienna RS232 Serial interface.
 *
 * @author s.simma - Initial contribution
 */
public class DeviceHandler extends BaseThingHandler implements IDeviceHandler {

    private Logger logger = LoggerFactory.getLogger(DeviceHandler.class);

    public DeviceHandler(Thing thing) {
        super(thing);
    }

    /**
     * Handle the commands
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        BridgeHandler bridgeHandler;

        Bridge bridge = this.getBridge();
        if (bridge == null) {
            return;
        }
        ThingHandler handler = bridge.getHandler();
        if (handler instanceof BridgeHandler) {
            bridgeHandler = (BridgeHandler) handler;
        } else {
            return;
        }

        // As long as Bridge is not online -> do nothing
        if (!(bridgeHandler.getThing().getStatus() == ThingStatus.ONLINE)) {
            return;
        }

        // Get Connector from bridge handler for sending data to the sienna gateway
        SerialConnector connector = bridgeHandler.getConnector();
        if (!connector.isConnected()) {
            logger.warn("Sienna: Connection to Sienna gateway is closed -> try to open");
            connector.connect();
            if (!connector.isConnected()) {
                logger.error("Sienna: Could not connect to Sienna gateway. No communication possible");
                return;
            }
        }

        // If thing is offline don't send
        if (this.getThing().getStatus() != ThingStatus.ONLINE) {
            if (SUPPORTED_DEVICE_THING_TYPES_UIDS.contains(this.getThing().getThingTypeUID())) {
                DeviceConfiguration configuration = getConfigAs(DeviceConfiguration.class);
                String sgroup = configuration.groupId;
                int element = configuration.elementId;
                logger.warn("Sienna: Device OFFLINE: {} g:{} e:{}", thing.getThingTypeUID().getId(), sgroup, element);
            }
            return;
        }

        if (SUPPORTED_DEVICE_THING_TYPES_UIDS.contains(this.getThing().getThingTypeUID())) {
            // Get Configuration for this Device
            DeviceConfiguration configuration = getConfigAs(DeviceConfiguration.class);

            String sgroup = configuration.groupId;
            int group = configuration.groupId.charAt(0) - 0x40;
            int element = configuration.elementId;

            NVPacket nvPacket = new NVPacket(connector);
            String thisThing = this.getThing().getThingTypeUID().getId();

            /* AM1, SAM1L, SAM1LT ------------------------------------- */
            if (thisThing.equals(DEVICE_AM1) || thisThing.equals(DEVICE_SAM1L) || thisThing.equals(DEVICE_SAM1LT)) {
                if (channelUID.getId().equals(CHANNEL_OUTLET)) {
                    nvPacket.setCommand(command.equals(OnOffType.ON) ? (byte) (ON | 0x80) : (byte) (OFF | 0x80));
                    nvPacket.setGroup((byte) group);
                    nvPacket.setElement((byte) element);
                    nvPacket.setAnswerCmd(
                            command.equals(OnOffType.ON) ? (byte) (STATUS_ON | 0x80) : (byte) (STATUS_OFF | 0x80));
                    nvPacket.setAnswerElementOffs((byte) 0x00);
                    nvPacket.sendToBridge(connector);
                    logger.info("Sienna: Send cmd: {} to: {} g:{} e:{}", command.toString(),
                            thing.getThingTypeUID().getId(), sgroup, element);
                } else {
                    logger.error("Sienna: channel: {} not possible for device: {}", channelUID.getId(),
                            getThing().getThingTypeUID().getId());
                }

                /* AM2L, SAM2L ------------------------------------- */
            } else if (thisThing.equals(DEVICE_AM2L) || thisThing.equals(DEVICE_SAM2L)) {
                if (channelUID.getId().equals(CHANNEL_OUTLET) || channelUID.getId().equals(CHANNEL_OUTLET2)) {
                    if (channelUID.getId().equals(CHANNEL_OUTLET2)) {
                        element = element + 1;
                    }
                    nvPacket.setCommand(command.equals(OnOffType.ON) ? (byte) (ON | 0x80) : (byte) (OFF | 0x80));
                    nvPacket.setGroup((byte) group);
                    nvPacket.setElement((byte) element);
                    if (channelUID.getId().equals(CHANNEL_OUTLET2)) {
                        nvPacket.setAnswerCmd(
                                command.equals(OnOffType.ON) ? (byte) (STATUS_ON | 0x80) : (byte) (STATUS_OFF | 0x80));
                    } else {
                        nvPacket.setAnswerCmd(
                                command.equals(OnOffType.ON) ? (byte) (STATUS_ON | 0x80) : (byte) (STATUS_OFF | 0x80));
                    }
                    nvPacket.setAnswerElementOffs((byte) (element - configuration.elementId));
                    nvPacket.sendToBridge(connector);
                    logger.info("Sienna: Send cmd: {} to: {} g:{} e:{}", command.toString(),
                            thing.getThingTypeUID().getId(), sgroup, element);
                } else {
                    logger.error("Sienna: channel: {} not possible for device: {}", channelUID.getId(),
                            getThing().getThingTypeUID().getId());
                }

                /* AM2X ------------------------------------- */
            } else if (thisThing.equals(DEVICE_AM2X)) {
                if (channelUID.getId().equals(CHANNEL_OUTLET) || channelUID.getId().equals(CHANNEL_OUTLET2)) {
                    if (channelUID.getId().equals(CHANNEL_OUTLET2)) {
                        element = element + 1;
                    }
                    nvPacket.setCommand(command.equals(OnOffType.ON) ? (byte) (ON | 0x80) : (byte) (OFF | 0x80));
                    nvPacket.setGroup((byte) group);
                    nvPacket.setElement((byte) element);
                    if (channelUID.getId().equals(CHANNEL_OUTLET2)) {
                        nvPacket.setAnswerCmd(
                                command.equals(OnOffType.ON) ? (byte) (STATUS_UP | 0x80) : (byte) (STATUS_OFF | 0x80));
                    } else {
                        nvPacket.setAnswerCmd(command.equals(OnOffType.ON) ? (byte) (STATUS_DOWN | 0x80)
                                : (byte) (STATUS_OFF | 0x80));
                    }
                    nvPacket.setAnswerElementOffs((byte) (element - configuration.elementId));
                    nvPacket.sendToBridge(connector);
                    logger.info("Sienna: Send cmd: {} to: {} g:{} e:{}", command.toString(),
                            thing.getThingTypeUID().getId(), sgroup, element);
                } else {
                    logger.error("Sienna: channel: {} not possible for device: {}", channelUID.getId(),
                            getThing().getThingTypeUID().getId());
                }

                /* Dimmer SAMDR, SAMDU --------------------------------------------------- */
            } else if (thisThing.equals(DEVICE_SAMDR) || thisThing.equals(DEVICE_SAMDU)) {
                if (channelUID.getId().equals(CHANNEL_OUTLET)) {
                    nvPacket.setCommand(command.equals(OnOffType.ON) ? (byte) (ON | 0x80) : (byte) (OFF | 0x80));
                    nvPacket.setPayload((byte) 0);
                    nvPacket.setGroup((byte) group);
                    nvPacket.setElement((byte) element);
                    nvPacket.setAnswerCmd(
                            command.equals(OnOffType.ON) ? (byte) (STATUS_ON | 0x80) : (byte) (STATUS_OFF | 0x80));
                    nvPacket.setAnswerElementOffs((byte) 0x00);
                    nvPacket.sendToBridge(connector);
                    logger.info("Sienna: Send cmd: {} to: {} g:{} e:{}", command.toString(),
                            thing.getThingTypeUID().getId(), sgroup, element);
                } else if (channelUID.getId().equals(CHANNEL_VALUE)) {
                    if (command instanceof Number) {
                        int value = ((Number) command).intValue() * 2;
                        if (value > 200) {
                            value = 200;
                        }
                        nvPacket.setCommand((byte) (SET_DIM_VALUE | 0x80));
                        nvPacket.setPayload((byte) value);
                        nvPacket.setGroup((byte) group);
                        nvPacket.setElement((byte) element);
                        nvPacket.setAnswerCmd((byte) 0x0);
                        nvPacket.setAnswerElementOffs((byte) 0x00);
                        nvPacket.sendToBridge(connector);
                        logger.info("Sienna: Send cmd: {} ({}) to: {} g:{} e:{}", command.toString(), value,
                                thing.getThingTypeUID().getId(), sgroup, element);
                    }
                } else {
                    logger.error("Sienna: channel: {} not possible for device: {}", channelUID.getId(),
                            getThing().getThingTypeUID().getId());
                }

                /* Rollershutter / Blind AM2, AMX2, SAM2 ------------------------------------- */
            } else if (thisThing.equals(DEVICE_SAM2) || thisThing.equals(DEVICE_AM2) || thisThing.equals(DEVICE_AMX2)) {
                if (command.equals(UpDownType.UP) || command.equals(UpDownType.DOWN)
                        || command.equals(StopMoveType.STOP)) {
                    int sCommand = 0;
                    if (command.equals(UpDownType.UP)) {
                        sCommand = GO_UP_DEF;
                    } else if (command.equals(UpDownType.DOWN)) {
                        sCommand = GO_DOWN_DEF;
                    } else if (command.equals(StopMoveType.STOP)) {
                        sCommand = STOP;
                    } else {
                        sCommand = STOP;
                    }

                    nvPacket.setCommand((byte) (sCommand | 0x80));
                    nvPacket.setGroup((byte) group);
                    nvPacket.setElement((byte) element);
                    nvPacket.setPayload((byte) 0);
                    nvPacket.setAnswerCmd((byte) 0x00);
                    nvPacket.setAnswerElementOffs((byte) 0x00);
                    nvPacket.sendToBridge(connector);
                    logger.info("Sienna: Send cmd: {} to: {} g:{} e:{}", command.toString(),
                            thing.getThingTypeUID().getId(), sgroup, element);
                } else if (channelUID.getId().equals(CHANNEL_COMMAND)) {
                    int sCommand = 0;
                    if (command.equals(UpDownType.UP) || command.toString().equals("UP")) {
                        sCommand = GO_UP_DEF;
                    } else if (command.equals(UpDownType.DOWN) || command.toString().equals("DOWN")) {
                        sCommand = GO_DOWN_DEF;
                    } else if (command.equals(StopMoveType.STOP) || command.toString().equals("STOP")) {
                        sCommand = STOP;
                    } else {
                        sCommand = STOP;
                    }
                    nvPacket.setCommand((byte) (sCommand | 0x80));
                    nvPacket.setGroup((byte) group);
                    nvPacket.setElement((byte) element);
                    nvPacket.setPayload((byte) 0);
                    nvPacket.setAnswerCmd((byte) 0x00);
                    nvPacket.setAnswerElementOffs((byte) 0x00);
                    nvPacket.sendToBridge(connector);
                    logger.info("Sienna: Send cmd: {} to: {} g:{} e:{}", command.toString(),
                            thing.getThingTypeUID().getId(), sgroup, element);
                } else if (channelUID.getId().equals(CHANNEL_OUTLET_UP)) {
                    nvPacket.setCommand(
                            command.equals(OnOffType.ON) ? (byte) (GO_UP_DEF | 0x80) : (byte) (STOP | 0x80));
                    nvPacket.setGroup((byte) group);
                    nvPacket.setElement((byte) element);
                    nvPacket.setPayload((byte) 0);
                    nvPacket.setAnswerCmd((byte) 0x00);
                    nvPacket.setAnswerElementOffs((byte) 0x00);
                    nvPacket.sendToBridge(connector);
                    logger.info("Sienna: Send cmd: {} to: {} g:{} e:{}", command.toString(),
                            thing.getThingTypeUID().getId(), sgroup, element);
                } else if (channelUID.getId().equals(CHANNEL_OUTLET_DOWN)) {
                    nvPacket.setCommand(
                            command.equals(OnOffType.ON) ? (byte) (GO_DOWN_DEF | 0x80) : (byte) (STOP | 0x80));
                    nvPacket.setGroup((byte) group);
                    nvPacket.setElement((byte) element);
                    nvPacket.setPayload((byte) 0);
                    nvPacket.setAnswerCmd((byte) 0x00);
                    nvPacket.setAnswerElementOffs((byte) 0x00);
                    nvPacket.sendToBridge(connector);
                    logger.info("Sienna: Send cmd: {} to: {} g:{} e:{}", command.toString(),
                            thing.getThingTypeUID().getId(), sgroup, element);
                } else if (channelUID.getId().equals(CHANNEL_POSITION)) {
                    if (command instanceof Number) {
                        int value = ((Number) command).intValue();
                        if (value > 100) {
                            value = 100;
                        } else if (value < 0) {
                            value = 0;
                        }

                        // Command = perc. closed -> calculate to open perc.
                        int cvalue = 100 - value;

                        // Addjust to device spezification (0-200)
                        cvalue = cvalue * 2;

                        nvPacket.setCommand((byte) (SET_MOTOR_POS | 0x80));
                        nvPacket.setGroup((byte) group);
                        nvPacket.setElement((byte) element);
                        nvPacket.setPayload((byte) cvalue);
                        nvPacket.setAnswerCmd((byte) 0x00);
                        nvPacket.setAnswerElementOffs((byte) 0x00);
                        nvPacket.sendToBridge(connector);

                        logger.info("Sienna: Send cmd: {} to: {} g:{} e:{} ({})", command.toString(),
                                thing.getThingTypeUID().getId(), sgroup, element, cvalue);
                    }
                } else if (channelUID.getId().equals(CHANNEL_ANGLE)) {
                    if (command instanceof Number) {
                        int value = ((Number) command).intValue();

                        // Set the blind angle to new position
                        int i = bridgeHandler.getBlindAngleActPos(sgroup, element);
                        i = i + value;
                        if (i > 100) {
                            i = 100;
                        } else if (i < 0) {
                            i = 0;
                        }
                        bridgeHandler.saveBlindAngleActPos(sgroup, element, i);
                        this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_ANGLE), new DecimalType(i));

                        int valuea = Math.abs(value);

                        // determine number of 1/100ms from percentage
                        int movet = ((configuration.spinTime / 100) * valuea) / 100;

                        if (movet >= (configuration.spinTime / 100)) {
                            movet = configuration.spinTime / 100;
                        } else {
                            if (movet < 1) {
                                return;
                            }
                        }

                        byte direction = 0x00;
                        if (value < 0) {
                            direction = (byte) 0x80;
                        }

                        nvPacket.setCommand((byte) (SET_MOTOR_ANG | 0x80));
                        nvPacket.setGroup((byte) group);
                        nvPacket.setElement((byte) element);
                        nvPacket.setPayload((byte) (movet | direction));
                        nvPacket.setAnswerCmd((byte) 0x00);
                        nvPacket.setAnswerElementOffs((byte) 0x00);
                        nvPacket.sendToBridge(connector);

                        logger.info("Sienna: Send cmd: {} to: {} g:{} e:{} 1/100s:({})", command.toString(),
                                thing.getThingTypeUID().getId(), sgroup, element, movet);
                    }
                } else {
                    logger.error("Sienna: channel: {} not possible for device: {}", channelUID.getId(),
                            getThing().getThingTypeUID().getId());
                }
            }
        } else {
            logger.error("Sienna: Binding doesn't support device: {}", getThing().getThingTypeUID().getId());
        }
    }

    @Override
    public DeviceConfiguration getDeviceConfiguration() {
        DeviceConfiguration configuration = getConfigAs(DeviceConfiguration.class);
        return configuration;
    }

    // Handle 1 Channel Devices
    @Override
    public void onDevice1OnOffChanged(int command, char cgroup, int element) {
        String thingStatus = "?";
        String thingChannel = "unknown";

        // Input On pressed
        if (command == GO) {
            thingStatus = OpenClosedType.CLOSED.toString();
            thingChannel = CHANNEL_INPUT;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT), OpenClosedType.CLOSED);

            // Input On released
        } else if (command == STOP_GO) {
            thingStatus = OpenClosedType.OPEN.toString();
            thingChannel = CHANNEL_INPUT;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT), OpenClosedType.OPEN);

            // Output On
        } else if (command == STATUS_ON) {
            thingStatus = OnOffType.ON.toString();
            thingChannel = CHANNEL_OUTLET;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET), OnOffType.ON);

            // Output Off
        } else if (command == STATUS_OFF) {
            thingStatus = OnOffType.OFF.toString();
            thingChannel = CHANNEL_OUTLET;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET), OnOffType.OFF);

            // Undefined
        } else if (command == DEVICE_UNDEF) {
            thingStatus = UnDefType.UNDEF.toString();
            thingChannel = "All";
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET), UnDefType.UNDEF);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT), UnDefType.UNDEF);
        }

        logger.info("Sienna: Update thing: {} g:{} e:{} channel: {} to {}", thing.getThingTypeUID().getId(), cgroup,
                element, thingChannel, thingStatus);
    }

    // Handle n-Channel Devices
    @Override
    public void onDeviceNchChanged(int command, char cgroup, int element, int offset) {
        String thingStatus = "?";
        String thingChannel = "unknown";

        // Input ON
        if ((command == GO) || (command == GO_UP) || (command == GO_DOWN)) {
            thingStatus = OpenClosedType.CLOSED.toString();
            switch (offset) {
                case 0:
                    thingChannel = CHANNEL_INPUT;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT), OpenClosedType.CLOSED);
                    break;
                case 1:
                    thingChannel = CHANNEL_INPUT2;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT2), OpenClosedType.CLOSED);
                    break;
                case 2:
                    thingChannel = CHANNEL_INPUT3;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT3), OpenClosedType.CLOSED);
                    break;
                case 3:
                    thingChannel = CHANNEL_INPUT4;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT4), OpenClosedType.CLOSED);
                    break;
                case 4:
                    thingChannel = CHANNEL_INPUT5;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT5), OpenClosedType.CLOSED);
                    break;
                case 5:
                    thingChannel = CHANNEL_INPUT6;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT6), OpenClosedType.CLOSED);
                    break;
                case 6:
                    thingChannel = CHANNEL_INPUT7;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT7), OpenClosedType.CLOSED);
                    break;
                case 7:
                    thingChannel = CHANNEL_INPUT8;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT8), OpenClosedType.CLOSED);
                    break;
            }

            // Input OFF
        } else if (command == STOP_GO) {
            thingStatus = OpenClosedType.OPEN.toString();
            switch (offset) {
                case 0:
                    thingChannel = CHANNEL_INPUT;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT), OpenClosedType.OPEN);
                    break;
                case 1:
                    thingChannel = CHANNEL_INPUT2;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT2), OpenClosedType.OPEN);
                    break;
                case 2:
                    thingChannel = CHANNEL_INPUT3;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT3), OpenClosedType.OPEN);
                    break;
                case 3:
                    thingChannel = CHANNEL_INPUT4;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT4), OpenClosedType.OPEN);
                    break;
                case 4:
                    thingChannel = CHANNEL_INPUT5;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT5), OpenClosedType.OPEN);
                    break;
                case 5:
                    thingChannel = CHANNEL_INPUT6;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT6), OpenClosedType.OPEN);
                    break;
                case 6:
                    thingChannel = CHANNEL_INPUT7;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT7), OpenClosedType.OPEN);
                    break;
                case 7:
                    thingChannel = CHANNEL_INPUT8;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT8), OpenClosedType.OPEN);
                    break;
            }

            // Output On
        } else if ((command == STATUS_DOWN) || (command == STATUS_UP) || (command == STATUS_ON)) {
            thingStatus = OnOffType.ON.toString();
            switch (offset) {
                case 0:
                    thingChannel = CHANNEL_OUTLET;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET), OnOffType.ON);
                    break;
                case 1:
                    thingChannel = CHANNEL_OUTLET2;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET2), OnOffType.ON);
                    break;
            }

            // Output Off
        } else if (command == STATUS_OFF) {
            thingStatus = OnOffType.OFF.toString();
            switch (offset) {
                case 0:
                    thingChannel = CHANNEL_OUTLET;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET), OnOffType.OFF);
                    break;
                case 1:
                    thingChannel = CHANNEL_OUTLET2;
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET2), OnOffType.OFF);
                    break;
            }

            // Undefined
        } else if (command == DEVICE_UNDEF) {
            thingStatus = UnDefType.UNDEF.toString();
            thingChannel = "All";
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT), UnDefType.UNDEF);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT2), UnDefType.UNDEF);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET), UnDefType.UNDEF);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET2), UnDefType.UNDEF);
        }

        logger.info("Sienna: Update thing: {} g:{} e:{} channel: {} to {}", thing.getThingTypeUID().getId(), cgroup,
                element, thingChannel, thingStatus);
    }

    // SAMDR, SAMDU
    @Override
    public void onDeviceDimmerChanged(int command, char cgroup, int element, double value) {
        String thingStatus = "?";
        String thingChannel = "unknown";

        // Input On pressed
        if (command == GO) {
            thingStatus = OpenClosedType.CLOSED.toString();
            thingChannel = CHANNEL_INPUT;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT), OpenClosedType.CLOSED);

            // Input On released
        } else if (command == STOP_GO) {
            thingStatus = OpenClosedType.OPEN.toString();
            thingChannel = CHANNEL_INPUT;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT), OpenClosedType.OPEN);

            // Output On
        } else if (command == STATUS_ON) {
            thingStatus = OnOffType.ON.toString();
            thingChannel = CHANNEL_OUTLET;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET), OnOffType.ON);

            // Output Off
        } else if (command == STATUS_OFF) {
            thingStatus = OnOffType.OFF.toString();
            thingChannel = CHANNEL_OUTLET;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET), OnOffType.OFF);

            // Dimm value
        } else if (command == REPORT_DIM_VALUE) {
            thingChannel = CHANNEL_VALUE;
            thingStatus = Double.toString(value);

            int i = (int) (value / 2);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_VALUE), new PercentType(i));

            // Undefined
        } else if (command == DEVICE_UNDEF) {
            thingStatus = UnDefType.UNDEF.toString();
            thingChannel = "All";
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT), UnDefType.UNDEF);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET), UnDefType.UNDEF);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_VALUE), UnDefType.UNDEF);
        }

        logger.info("Sienna: Update thing: {} g:{} e:{} channel: {} to {}", thing.getThingTypeUID().getId(), cgroup,
                element, thingChannel, thingStatus);
    }

    // SAM2, AM2, AMX2 (Rollershutter, Motor Up/Down)
    @Override
    public void onDeviceUpDownChanged(int command, char cgroup, int element, double cvalue) {
        String thingStatus = "?";
        String thingChannel = "unknown";
        int value;

        // Input Up pressed
        if (command == GO_UP) {
            thingStatus = OpenClosedType.CLOSED.toString();
            thingChannel = CHANNEL_INPUT_UP;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT_UP), OpenClosedType.CLOSED);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INFO), new StringType("GO-UP"));

            // Input Down pressed
        } else if (command == GO_DOWN) {
            thingStatus = OpenClosedType.CLOSED.toString();
            thingChannel = CHANNEL_INPUT_DOWN;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT_DOWN), OpenClosedType.CLOSED);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INFO), new StringType("GO-DOWN"));

            // Input Up/Down released
        } else if (command == STOP_GO) {
            thingStatus = OpenClosedType.OPEN.toString();
            thingChannel = CHANNEL_INPUT_UP + "/" + CHANNEL_INPUT_DOWN;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT_UP), OpenClosedType.OPEN);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT_DOWN), OpenClosedType.OPEN);

            // Status go UP
        } else if (command == STATUS_GO_UP) {
            thingStatus = "GO-UP";
            thingChannel = CHANNEL_OUTLET_UP;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INFO), new StringType("GO-UP"));

            // Status go DOWN
        } else if (command == STATUS_GO_DOWN) {
            thingStatus = "GO-DOWN";
            thingChannel = CHANNEL_OUTLET_DOWN;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INFO), new StringType("GO-DOWN"));

            // Status is UP
        } else if (command == STATUS_UP) {
            thingStatus = "UP";
            thingChannel = CHANNEL_OUTLET_UP;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET_UP), OnOffType.OFF);

            // Status is DOWN
        } else if (command == STATUS_DOWN) {
            thingStatus = "DOWN";
            thingChannel = CHANNEL_OUTLET_DOWN;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET_DOWN), OnOffType.OFF);

            // Motor Position
        } else if (command == REPORT_MOTOR_POS) {
            thingChannel = CHANNEL_POSITION;

            if (cvalue > 200) {
                value = 200;
            } else if (cvalue < 0) {
                value = 0;
            } else {
                value = (int) cvalue;
            }
            // Adjust from 0-200 to 0-100% and open based to closed based
            value = 100 - (value / 2);

            thingStatus = Integer.toString(value);

            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_POSITION), new PercentType(value));
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INFO), new StringType(""));

            BridgeHandler bridgeHandler;
            Bridge bridge = this.getBridge();
            if (bridge == null) {
                return;
            }
            ThingHandler handler = bridge.getHandler();
            if (handler instanceof BridgeHandler) {
                bridgeHandler = (BridgeHandler) handler;
            } else {
                return;
            }
            int i = bridgeHandler.getBlindActPos(Character.toString(cgroup), element);

            // If deviation < 2% dont't change the angle (already being done by SET_ANGLE command)...no better idea
            // On startup the actual position is set to -1 by the bridge handler
            if (i == -1) {
                bridgeHandler.saveBlindAngleActPos(Character.toString(cgroup), element, 50);
                this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_ANGLE), new DecimalType(50));
            } else {
                if (value > (i + 2)) {
                    // Set the blind angle to 100% if actual position > last position
                    bridgeHandler.saveBlindAngleActPos(Character.toString(cgroup), element, 100);
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_ANGLE), new DecimalType(100));
                } else if (value < (i - 2)) {
                    // Set the blind angle to 0% if actual position < last position
                    bridgeHandler.saveBlindAngleActPos(Character.toString(cgroup), element, 0);
                    this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_ANGLE), new DecimalType(0));
                }
            }

            // Save the actual position as last position for internal calculation UP/DOWN
            bridgeHandler.saveBlindActPos(Character.toString(cgroup), element, value);

            // Motor Angle (for future use)
        } else if (command == REPORT_MOTOR_ANG) {
            thingChannel = CHANNEL_ANGLE;
            thingStatus = Integer.toString((int) cvalue);
            logger.warn("Sienna: Received REPORT_MOTOR_ANGLE (not implemented jet)");
        } else if (command == DEVICE_UNDEF) {
            thingStatus = UnDefType.UNDEF.toString();
            thingChannel = "All";
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT_UP), UnDefType.UNDEF);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_INPUT_DOWN), UnDefType.UNDEF);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET_UP), UnDefType.UNDEF);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTLET_DOWN), UnDefType.UNDEF);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_POSITION), UnDefType.UNDEF);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_ANGLE), UnDefType.UNDEF);
        }

        logger.info("Sienna: Update thing: ({}) {} g:{} e:{} channel: {} to {}", command,
                thing.getThingTypeUID().getId(), cgroup, element, thingChannel, thingStatus);
    }

    // RFGS-CONTACT
    @Override
    public void onDeviceEnoContactChanged(int command, char cgroup, int element) {
        String thingStatus = "?";
        String thingChannel = "unknown";

        // Contact closed
        if (command == ON) {
            thingStatus = OpenClosedType.CLOSED.toString();
            thingChannel = CHANNEL_STATE;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_STATE), OpenClosedType.CLOSED);

            // Contact open
        } else if (command == OFF) {
            thingStatus = OpenClosedType.OPEN.toString();
            thingChannel = CHANNEL_STATE;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_STATE), OpenClosedType.OPEN);

            // Undefined
        } else if (command == DEVICE_UNDEF) {
            thingStatus = UnDefType.UNDEF.toString();
            thingChannel = CHANNEL_STATE;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_STATE), UnDefType.UNDEF);
        }

        logger.info("Sienna: Update thing: {} g:{} e:{} channel: {} to {}", thing.getThingTypeUID().getId(), cgroup,
                element, thingChannel, thingStatus);
    }

    // RFGS-MOTION
    @Override
    public void onDeviceEnoMotionChanged(int command, char cgroup, int element) {
        String thingStatus = "?";
        String thingChannel = "unknown";

        // Contact closed
        if (command == ON) {
            thingStatus = OpenClosedType.OPEN.toString();
            thingChannel = CHANNEL_STATE;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_STATE), OpenClosedType.OPEN);

            // Contact open
        } else if (command == OFF) {
            thingStatus = OpenClosedType.CLOSED.toString();
            thingChannel = CHANNEL_STATE;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_STATE), OpenClosedType.CLOSED);

            // Undefined
        } else if (command == DEVICE_UNDEF) {
            thingStatus = UnDefType.UNDEF.toString();
            thingChannel = CHANNEL_STATE;
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_STATE), UnDefType.UNDEF);
        }

        logger.info("Sienna: Update thing: {} g:{} e:{} channel: {} to {}", thing.getThingTypeUID().getId(), cgroup,
                element, thingChannel, thingStatus);
    }

    // RFGS-TEMP
    @Override
    public void onDeviceEnoTempChanged(int command, char cgroup, int element, double value) {
        String thingStatus = "?";
        String thingChannel = CHANNEL_TEMPERATURE;

        if (command == DEVICE_UNDEF) {
            thingStatus = UnDefType.UNDEF.toString();
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_TEMPERATURE), UnDefType.UNDEF);
        } else {
            double d = value;
            d = d / 10 * 2.4;
            d = round(d, 2);
            thingStatus = String.valueOf(d);
            this.updateState(new ChannelUID(getThing().getUID(), CHANNEL_TEMPERATURE), new DecimalType(thingStatus));
        }

        logger.info("Sienna: Update thing: {} g:{} e:{} channel: {} to {}", thing.getThingTypeUID().getId(), cgroup,
                element, thingChannel, thingStatus);
    }

    @Override
    public void setThingOffline() {
        this.updateStatus(ThingStatus.OFFLINE);
    }

    @Override
    public void setThingOnline() {
        this.updateStatus(ThingStatus.ONLINE);
    }

    // Round double
    private double round(double zahl, int stellen) {
        return (int) zahl + (Math.round(Math.pow(10, stellen) * (zahl - (int) zahl))) / (Math.pow(10, stellen));
    }

}
