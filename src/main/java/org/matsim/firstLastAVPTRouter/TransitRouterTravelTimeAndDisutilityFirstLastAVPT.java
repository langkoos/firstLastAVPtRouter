/* *********************************************************************** *
 * project: org.matsim.*
 * TransitRouterNetworkTravelTimeAndDisutilityVariableWW.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.firstLastAVPTRouter;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.eventsBasedPTRouter.stopStopTimes.StopStopTime;
import org.matsim.contrib.eventsBasedPTRouter.waitTimes.WaitTime;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.TravelTimeCalculatorConfigGroup;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.router.CustomDataManager;
import org.matsim.pt.router.PreparedTransitSchedule;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.router.TransitTravelDisutility;
import org.matsim.vehicles.Vehicle;

import java.util.HashMap;
import java.util.Map;

/**
 * TravelTime and TravelDisutility calculator to be used with the transit network used for transit routing.
 * This version considers waiting time at stops, and takes travel time between stops from a {@link StopStopTime} object.
 *
 * @author sergioo
 */

public class TransitRouterTravelTimeAndDisutilityFirstLastAVPT implements TravelTime, TravelDisutility, TransitTravelDisutility {

	private Link previousLink;
	private double previousTime;
	private double cachedLinkTime;
	private double cachedWaitTime;
	private final Map<Id<Link>, double[]> linkTravelTimes = new HashMap<>();
	private final Map<Id<Link>, double[]> linkTravelTimesAV = new HashMap<>();
	private final Map<Id<Link>, double[]> linkWaitingTimes = new HashMap<>();
	private final Map<Id<Link>, double[]> linkWaitingTimesAV = new HashMap<>();
	private final int numSlots;
	private final double timeSlot;
	private final TransitRouterParams config;
	private final TransitRouterUtilityParams params;


	public TransitRouterTravelTimeAndDisutilityFirstLastAVPT(TransitRouterParams config, TransitRouterUtilityParams params, TransitRouterNetworkFirstLastAVPT routerNetwork, WaitTime waitTime, WaitTime waitTimeAV, StopStopTime stopStopTime, StopStopTime stopStopTimeAV, TravelTimeCalculatorConfigGroup tTConfigGroup, QSimConfigGroup qSimConfigGroup, PreparedTransitSchedule preparedTransitSchedule) {
		this(config, params, routerNetwork, waitTime, waitTimeAV, stopStopTime, stopStopTimeAV, tTConfigGroup, qSimConfigGroup.getStartTime().seconds(), qSimConfigGroup.getEndTime().seconds(), preparedTransitSchedule);
	}
	public TransitRouterTravelTimeAndDisutilityFirstLastAVPT(TransitRouterParams config, TransitRouterUtilityParams params, TransitRouterNetworkFirstLastAVPT routerNetwork, WaitTime waitTime, WaitTime waitTimeAV, StopStopTime stopStopTime, StopStopTime stopStopTimeAV, TravelTimeCalculatorConfigGroup tTConfigGroup, double startTime, double endTime, PreparedTransitSchedule preparedTransitSchedule) {
		this.config = config;
		this.params = params;
		timeSlot = tTConfigGroup.getTraveltimeBinSize();
		numSlots = (int) ((endTime - startTime)/timeSlot);
		for(TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkLink link:routerNetwork.getLinks().values())
			if(link.route != null) {
				double[] times = new double[numSlots];
				for(int slot = 0; slot < numSlots; slot++)
					times[slot] = stopStopTime.getStopStopTime(link.fromNode.stop.getId(), link.toNode.stop.getId(), startTime + slot * timeSlot);
				linkTravelTimes.put(link.getId(), times);
			}
			else if(link.toNode.route!=null && link.toNode.line!=null) {
				double[] times = new double[numSlots];
				for(int slot = 0; slot<numSlots; slot++) {
					times[slot] = waitTime.getRouteStopWaitTime(link.toNode.line.getId(), link.toNode.route.getId(), link.fromNode.stop.getId(), startTime + slot * timeSlot);
					linkWaitingTimes.put(link.getId(), times);
				}
			}
			else if(link.fromNode.route==null && link.mode.equals(TransitRouterFirstLastAVPT.AV_MODE)) {
				double[] times = new double[numSlots];
				for(int slot = 0; slot<numSlots; slot++)
					times[slot] = stopStopTimeAV.getStopStopTime(link.fromNode.stop.getId(), link.toNode.stop.getId(), startTime+slot*timeSlot);
				linkTravelTimesAV.put(link.getId(), times);
				times = new double[numSlots];
				for(int slot = 0; slot<numSlots; slot++)
					times[slot] = waitTimeAV.getRouteStopWaitTime(null, null, link.fromNode.stop.getId(), startTime+slot*timeSlot);
				linkWaitingTimesAV.put(link.getId(), times);
			}
	}
	public double getLinkTravelTime(final Link link, final double time, Person person, Vehicle vehicle) {
		previousLink = link;
		previousTime = time;
		cachedWaitTime = 0;
		TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkLink wrapped = (TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkLink) link;
		int index = time/timeSlot<numSlots ? (int)(time/timeSlot) : (numSlots-1);
		double length = wrapped.getLength()<3?3:wrapped.getLength();
		if (wrapped.route!=null)
			//in line link
			cachedLinkTime = linkTravelTimes.get(wrapped.getId())[index];
		else if(wrapped.toNode.route!=null && wrapped.toNode.line!=null)
			//wait link
			cachedLinkTime = linkWaitingTimes.get(wrapped.getId())[index];
		else if(wrapped.fromNode.route==null && wrapped.mode.equals(TransportMode.transit_walk))
			//walking link
			cachedLinkTime = length/this.config.beelineWalkSpeed;
		else if(wrapped.fromNode.route==null) {
			// it's a transfer link (av)
			cachedLinkTime = linkTravelTimesAV.get(wrapped.getId())[index];
			cachedWaitTime = linkWaitingTimesAV.get(wrapped.getId())[index];
		}
		else
			//inside link
			cachedLinkTime = 0;
		return cachedLinkTime + cachedWaitTime;
	}
	@Override
	public double getLinkTravelDisutility(final Link link, final double time, final Person person, final Vehicle vehicle, final CustomDataManager dataManager) {
		boolean cachedTravelDisutility = previousLink == link && previousTime == time;
		TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkLink wrapped = (TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkLink) link;
		int index = time/timeSlot<numSlots ? (int)(time/timeSlot) : (numSlots-1);
		double length = wrapped.getLength()<3?3:wrapped.getLength();
		if (wrapped.route != null)

			return -(cachedTravelDisutility?cachedLinkTime:linkTravelTimes.get(wrapped.getId())[index])*params.marginalUtilityOfTravelTimePt_s
					- link.getLength()*params.marginalUtilityOfTravelDistancePt_m;
		else if (wrapped.toNode.route!=null && wrapped.toNode.line!=null)
			// it's a wait link
			return -(cachedTravelDisutility?cachedLinkTime:linkWaitingTimes.get(wrapped.getId())[index])*params.marginalUtilityWait_s;
		else if(wrapped.fromNode.route==null && wrapped.mode.equals(TransportMode.transit_walk))
			// it's a transfer link (walk)
			return -(cachedTravelDisutility?cachedLinkTime:length/this.config.beelineWalkSpeed)*params.marginalUtilityWalk_s
					-length*params.marginalUtilityWalk_m;
		else if(wrapped.fromNode.route==null)
			// it's a transfer link (av)
			return -(cachedTravelDisutility?cachedLinkTime:linkTravelTimesAV.get(wrapped.getId())[index])*params.marginalUtilityAV_s
					-(cachedTravelDisutility?cachedWaitTime:linkWaitingTimesAV.get(wrapped.getId())[index])*params.marginalUtilityWait_s
					-length*params.marginalUtilityAV_m;
		else
			//inside link
			return -this.params.utilityLineSwitch;
	}
	@Override
	public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
		TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkLink wrapped = (TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkLink) link;
		int index = time/timeSlot<numSlots ? (int)(time/timeSlot) : (numSlots-1);
		double length = wrapped.getLength()<3?3:wrapped.getLength();
		if (wrapped.route != null)
			return - linkTravelTimes.get(wrapped.getId())[index]*params.marginalUtilityOfTravelTimePt_s
					- link.getLength()*params.marginalUtilityOfTravelDistancePt_m;
		else if (wrapped.toNode.route!=null && wrapped.toNode.line!=null)
			// it's a wait link
			return - linkWaitingTimes.get(wrapped.getId())[index]*params.marginalUtilityWait_s;
		else if(wrapped.fromNode.route==null && wrapped.mode.equals(TransportMode.transit_walk))
			// it's a transfer link (walk)
			return -(length/this.config.beelineWalkSpeed)*params.marginalUtilityWalk_s
					-length*params.marginalUtilityWalk_m;
		else if(wrapped.fromNode.route==null)
			// it's a transfer link (av)
			return -linkTravelTimesAV.get(wrapped.getId())[index]*params.marginalUtilityAV_s
					-linkWaitingTimesAV.get(wrapped.getId())[index]*params.marginalUtilityWait_s
					-length*params.marginalUtilityAV_m;
		else
			//inside link
			return - this.params.utilityLineSwitch;
	}
	@Override
	public double getLinkMinimumTravelDisutility(Link link) {
		return 0;
	}

	@Override
	public double getWalkTravelDisutility(Person person, Coord coord, Coord toCoord) {
		double timeCost = -getWalkTravelTime(person, coord, toCoord) * params.marginalUtilityWalk_s;
		double distanceCost = - CoordUtils.calcEuclideanDistance(coord,toCoord) * config.beelineWalkDistanceFactor * params.marginalUtilityWalk_m;
		return timeCost + distanceCost + params.initialCostWalk;
	}
	@Override
	public double getWalkTravelTime(Person person, Coord coord, Coord toCoord) {
		double distance = CoordUtils.calcEuclideanDistance(coord, toCoord);
		return distance / config.beelineWalkSpeed; /* return initial time */
	}

}
