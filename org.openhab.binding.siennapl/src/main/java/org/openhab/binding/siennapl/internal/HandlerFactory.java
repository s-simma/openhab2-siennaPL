/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.siennapl.internal;

import static org.openhab.binding.internal.siennapl.BindingConstants.*;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.oepnhab.binding.siennapl.internal.discovery.DeviceDiscoveryService;
import org.openhab.binding.siennapl.internal.handler.BridgeHandler;
import org.openhab.binding.siennapl.internal.handler.DeviceHandler;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author s.simma - Initial contribution
 */
public class HandlerFactory extends BaseThingHandlerFactory {

    private Logger logger = LoggerFactory.getLogger(HandlerFactory.class);

    private Map<ThingUID, ServiceRegistration<?>> discoveryServiceRegs = new HashMap<>();

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    /**
     * Create handler of things.
     */
    @Override
    protected ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (thingTypeUID.equals(BRIDGE_THING_TYPE)) {
            BridgeHandler bridgeHandler = new BridgeHandler((Bridge) thing);
            registerDiscoveryService(bridgeHandler);
            return bridgeHandler;
        } else if (supportsThingType(thing.getThingTypeUID())) {
            return new DeviceHandler(thing);
        } else {
            logger.warn("ThingHandler not found for {}", thing.getThingTypeUID());
        }

        return null;
    }

    /**
     * Remove handler of things.
     */
    @Override
    protected synchronized void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof BridgeHandler) {
            ServiceRegistration<?> serviceReg = this.discoveryServiceRegs.get(thingHandler.getThing().getUID());
            if (serviceReg != null) {
                serviceReg.unregister();
                discoveryServiceRegs.remove(thingHandler.getThing().getUID());
            }
        }
        super.removeHandler(thingHandler);
    }

    /**
     * Register a new discovery service for a Sienna Powerline gateway.
     *
     * @param handler
     */
    private void registerDiscoveryService(BridgeHandler bridgeHandler) {
        DeviceDiscoveryService discoveryService = new DeviceDiscoveryService(bridgeHandler);

        this.discoveryServiceRegs.put(bridgeHandler.getThing().getUID(), bundleContext
                .registerService(DiscoveryService.class.getName(), discoveryService, new Hashtable<String, Object>()));
    }

}
