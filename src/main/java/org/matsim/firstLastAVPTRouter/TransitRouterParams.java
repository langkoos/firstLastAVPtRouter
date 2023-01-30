package org.matsim.firstLastAVPTRouter;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.pt.config.TransitRouterConfigGroup;

public class TransitRouterParams {

    static final double avSpeed = 6.0;
    static final double avTaxiSpeed = 9.0;
    static final double maxBeelineAVConnectionDistance = 2000;
    double searchRadius;
    double maxBeelineWalkConnectionDistance;
    double extensionRadius;
    double beelineWalkDistanceFactor;
    double beelineWalkSpeed;
    TransitRouterParams(TransitRouterConfigGroup transitRouterConfigGroup, PlansCalcRouteConfigGroup plansCalcRouteConfigGroup) {
        this.searchRadius = transitRouterConfigGroup.getSearchRadius();
        this.maxBeelineWalkConnectionDistance = transitRouterConfigGroup.getMaxBeelineWalkConnectionDistance();
        this.extensionRadius = transitRouterConfigGroup.getExtensionRadius();
        this.beelineWalkDistanceFactor = plansCalcRouteConfigGroup.getModeRoutingParams().get( TransportMode.walk ).getBeelineDistanceFactor();
        this.beelineWalkSpeed = plansCalcRouteConfigGroup.getTeleportedModeSpeeds().get(TransportMode.walk)/plansCalcRouteConfigGroup.getModeRoutingParams().get( TransportMode.walk ).getBeelineDistanceFactor();
    }

}
