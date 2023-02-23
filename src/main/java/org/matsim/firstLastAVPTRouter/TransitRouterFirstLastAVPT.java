/* *********************************************************************** *
 * project: org.matsim.*
 * TransitRouterVariableImpl.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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

import org.jetbrains.annotations.NotNull;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.eventsBasedPTRouter.MultiDestinationDijkstra;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.InitialNode;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.RoutingRequest;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.router.util.PreProcessDijkstra;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.Facility;
import org.matsim.pt.router.MultiNodeDijkstra;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitStopArea;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.*;

public class TransitRouterFirstLastAVPT implements RoutingModule {

    public static final String AV_MODE = "drt";
	private final TransitRouterNetworkFirstLastAVPT transitNetwork;
	private final MultiNodeDijkstra dijkstra;
	private final MultiDestinationDijkstra mDijkstra;
	private final TransitRouterParams config;
	private final TransitRouterUtilityParams params;
	private final TransitRouterTravelTimeAndDisutilityFirstLastAVPT disutilityFunction;
	private final Network cleanNetwork;
	private final Map<Id<TransitStopArea>, QuadTree<TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkNode>> stopsByArea;


	public TransitRouterFirstLastAVPT(final TransitRouterParams config, final TransitRouterUtilityParams params,
									  final TransitRouterTravelTimeAndDisutilityFirstLastAVPT ttCalculator,
									  final TransitRouterNetworkFirstLastAVPT routerNetwork, Network cleanNetwork, Map<Id<TransitStopArea>,
									  QuadTree<TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkNode>> stopsByArea,
									  PreProcessDijkstra preProcessDijkstra) {
		this.config = config;
		this.params = params;
		this.transitNetwork = routerNetwork;
		this.disutilityFunction = ttCalculator;
		this.dijkstra = new MultiNodeDijkstra(this.transitNetwork, this.disutilityFunction, this.disutilityFunction);
		mDijkstra = new MultiDestinationDijkstra(routerNetwork, this.disutilityFunction, this.disutilityFunction, preProcessDijkstra);
		this.cleanNetwork = cleanNetwork;
		this.stopsByArea = stopsByArea;
	}
	
	private Map[] locateWrappedNearestTransitNodes(Person person, Coord coord, Id<Link> link, double departureTime) {
		Collection<TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkNode> nearestNodes = this.transitNetwork.getNearestNodes(coord, config.searchRadius);
		if (nearestNodes.size() < 1) {
			// also enlarge search area if only one stop found, maybe a second one is near the border of the search area
			TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkNode nearestNode = this.transitNetwork.getNearestNode(coord);
			double distance = CoordUtils.calcEuclideanDistance(coord, nearestNode.stop.getCoord());
			nearestNodes = this.transitNetwork.getNearestNodes(coord, distance + this.config.extensionRadius);
		}
		for (Id<TransitStopArea> stopAreaId: stopsByArea.keySet()){
			TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkNode nearestNode = stopsByArea.get(stopAreaId).getClosest(coord.getX(),coord.getY());
			if (nearestNodes.stream().allMatch(node -> node.stop.getStopAreaId() != stopAreaId)) {
				nearestNodes.add(nearestNode);
			}
		}
		Map<Node, InitialNode> wrappedNearestNodes = new LinkedHashMap<>();
		for (TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkNode node : nearestNodes) {
			Coord toCoord = node.stop.getCoord();
			double initialTime = getWalkTime(person, coord, toCoord);
			double initialCost = getWalkDisutility(person, coord, toCoord);
			wrappedNearestNodes.put(node, new InitialNode(initialCost, initialTime + departureTime));
		}
		Collection<TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkNode> nearestNodesAV = this.transitNetwork.getNearestAVNodes(coord, TransitRouterParams.maxBeelineAVConnectionDistance);
		if (nearestNodesAV.size() < 1) {
			// also enlarge search area if only one stop found, maybe a second one is near the border of the search area
			TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkNode nearestNode = this.transitNetwork.getNearestNode(coord);
			double distance = CoordUtils.calcEuclideanDistance(coord, nearestNode.stop.getCoord());
			nearestNodesAV = this.transitNetwork.getNearestNodes(coord, distance + this.config.extensionRadius);
		}
		Map<Node, InitialNode> wrappedNearestNodesAV = new LinkedHashMap<>();
		return new Map[]{wrappedNearestNodes, wrappedNearestNodesAV};
	}
	
	private double getWalkTime(Person person, Coord coord, Coord toCoord) {
		return this.disutilityFunction.getWalkTravelTime(person, coord, toCoord);
	}
	
	private double getWalkDisutility(Person person, Coord coord, Coord toCoord) {
		return this.disutilityFunction.getWalkTravelDisutility(person, coord, toCoord);
	}
	
	public Map<Id<Node>, Path> calcPathRoutes(final Id<Node> fromNodeId, final Set<Id<Node>> toNodeIds, final double startTime, final Person person) {
		Set<Node> toNodes = new HashSet<>();
		for(Id<Node> toNode:toNodeIds)
			if(transitNetwork.getNodes().get(toNode)!=null)
				toNodes.add(transitNetwork.getNodes().get(toNode));
		Node node = transitNetwork.getNodes().get(fromNodeId);
		if(node!=null)
			return mDijkstra.calcLeastCostPath(node, toNodes, startTime, person);
		else
			return new HashMap<>();
	}


	public List<PlanElement> calcRoute(final Facility fromFacility, final Facility toFacility, final double departureTime, final Person person) {
		// find possible start stops
		Map<Node, InitialNode>[] wrappedFromNodes = this.locateWrappedNearestTransitNodes(person, fromFacility.getCoord(), fromFacility.getLinkId(), departureTime);
		Map<Node, InitialNode> wrappedAllFromNodes = getWrappedAllFromOrToNodes(fromFacility, wrappedFromNodes);
		// find possible end stops
		Map<Node, InitialNode>[] wrappedToNodes  = this.locateWrappedNearestTransitNodes(person, toFacility.getCoord(), toFacility.getLinkId(), departureTime);
		Map<Node, InitialNode> wrappedAllToNodes = getWrappedAllFromOrToNodes(toFacility, wrappedToNodes);

		Path p = this.dijkstra.calcLeastCostPath(wrappedAllFromNodes, wrappedAllToNodes, person);
		if (p == null) return null;

		double pathCost = p.travelCost + wrappedAllFromNodes.get(p.nodes.get(0)).initialCost + wrappedAllToNodes.get(p.nodes.get(p.nodes.size() - 1)).initialCost + (p.links.size()>0?this.params.utilityLineSwitch+params.initialCostPT:0);
		double directWalkCost = getWalkDisutility(person,fromFacility.getCoord(), toFacility.getCoord());
		boolean avFrom = false;
		InitialNode initialNode = wrappedFromNodes[1].get(p.nodes.get(0));
		if(initialNode!=null) {
			InitialNode walkNode = wrappedFromNodes[0].get(p.nodes.get(0));
			if(walkNode!=null)
				if(initialNode.initialCost<walkNode.initialCost) avFrom = true;
			else avFrom = true;
		}
		boolean avTo = false;
		initialNode = wrappedToNodes[1].get(p.nodes.get(p.nodes.size()-1));
		if(initialNode!=null) {
			InitialNode walkNode = wrappedToNodes[0].get(p.nodes.get(p.nodes.size()-1));
			if(walkNode!=null)
				if(initialNode.initialCost<walkNode.initialCost) avTo = true;
			else avTo = true;
		}
		List<PlanElement> legs = new ArrayList<>();
		boolean direct = false;
		if (directWalkCost <= pathCost) {
			pathCost = directWalkCost;
			Leg leg = PopulationUtils.createLeg(TransportMode.transit_walk);
			double walkDistance = CoordUtils.calcEuclideanDistance(fromFacility.getCoord(), toFacility.getCoord())*1.3;
			Route walkRoute = RouteUtils.createGenericRouteImpl(fromFacility.getLinkId(), toFacility.getLinkId());
			walkRoute.setDistance(walkDistance);
			leg.setRoute(walkRoute);
			leg.setTravelTime(walkDistance/this.config.beelineWalkSpeed);
			legs.add(leg);
			direct = true;
		}
		if(!direct) {
			Coord fromC = fromFacility.getCoord();
			Coord toC = toFacility.getCoord();
			Id<Link> fromL = fromFacility.getLinkId();
			Id<Link> toL = toFacility.getLinkId();
			legs.addAll(convertPathToLegList(departureTime, p, fromC, toC, person, fromL, toL));
		}
		return fillWithActivities(legs);
	}

	@NotNull
	private Map<Node, InitialNode> getWrappedAllFromOrToNodes(Facility inputFacility, Map<Node, InitialNode>[] wrappedNodes) {
		Map<Node, InitialNode> wrappedAllNodes = new HashMap<>(wrappedNodes[0]);
		if(cleanNetwork.getLinks().containsKey(inputFacility.getLinkId())) {
			Set<Node> toRemove = new HashSet<>();
			wrappedNodes[1].forEach((key, value) -> {
				InitialNode other = wrappedAllNodes.get(key);
				if (other != null) {
					if (value.initialCost < other.initialCost)
						wrappedAllNodes.put(key, value);
					else
						toRemove.add(key);
				} else
					wrappedAllNodes.put(key, value);
			});
			toRemove.forEach(node -> wrappedNodes[1].remove(node));
		}
		else
			wrappedNodes[1].clear();
		return wrappedAllNodes;
	}
//
//	@Override
//	public StageActivityTypes getStageActivityTypes() {
//		return new StageActivityTypesImpl(PtConstants.TRANSIT_ACTIVITY_TYPE);
//	}
	
	protected List<PlanElement> convertPathToLegList(double departureTime, Path p, Coord fromCoord, Coord toCoord, Person person, Id<Link> startLinkId, Id<Link> endLinkId) {
		String mode = TransportMode.transit_walk;
		List<PlanElement> legs = new ArrayList<>();
		Leg leg;
		double distance, moveTime, travelTime = 0;
		Route route;
		Coord coord = fromCoord;
		boolean start = true;
		TransitStopFacility stop = ((TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkNode)p.nodes.get(0)).stop;
		double time = departureTime;
		List<Link> links = p.links;
		for (Link link : links) {
			TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkLink l = (TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkLink) link;
			if (l.route != null) {
				//in line link
				double ttime = disutilityFunction.getLinkTravelTime(l, time, person, null);
				travelTime += ttime;
				time += ttime;
			} else if (l.fromNode.route != null && l.fromNode.line != null) {
				//inside link
				start = false;
				mode = TransportMode.pt;
				leg = PopulationUtils.createLeg(mode);
				DefaultTransitPassengerRoute ptRoute = new DefaultTransitPassengerRoute(stop, l.fromNode.line, l.fromNode.route, l.fromNode.stop);
				leg.setRoute(ptRoute);
				leg.setTravelTime(travelTime);
				legs.add(leg);
				travelTime = 0;
				stop = l.fromNode.stop;
				} else if (l.toNode.route != null && l.toNode.line != null) {
				//wait link
				if (coord != null) {
					Id<Link> startId = start ? startLinkId : stop.getLinkId(), endId = l.fromNode.stop.getLinkId();
					distance = CoordUtils.calcEuclideanDistance(coord, l.fromNode.getCoord());
					if (distance > 0 || !startLinkId.equals(l.fromNode.stop.getLinkId())) {
						start = false;
						leg = PopulationUtils.createLeg(mode);
						moveTime = distance / (mode.equals(TransportMode.transit_walk) ? this.config.beelineWalkSpeed : TransitRouterParams.avSpeed);
						if (mode.equals(TransportMode.transit_walk))
							route = RouteUtils.createGenericRouteImpl(startId, endId);
						else {
							route = new DrtRoute(startId, endId);
							route.setRouteDescription(600 + " " + moveTime);
							route.setTravelTime(1.5 * moveTime + 600);
						}
						route.setDistance(distance);
						leg.setRoute(route);
						leg.setTravelTime(moveTime);
						time += moveTime;
						legs.add(leg);
					}
				}
				stop = l.fromNode.stop;
				coord = null;
			} else if (l.mode != null) {
				if (!mode.equals("pt") && !mode.equals(l.mode)) {
					Id<Link> startId = start ? startLinkId : stop.getLinkId(), endId = l.fromNode.stop.getLinkId();
					distance = CoordUtils.calcEuclideanDistance(coord, l.fromNode.getCoord());
					if (distance > 0 || !startLinkId.equals(l.fromNode.stop.getLinkId())) {
						start = false;
						leg = PopulationUtils.createLeg(mode);
						moveTime = distance / (mode.equals(TransportMode.transit_walk) ? this.config.beelineWalkSpeed : TransitRouterParams.avSpeed);
						if (mode.equals(TransportMode.transit_walk))
							route = RouteUtils.createGenericRouteImpl(startId, endId);
						else {
							route = new DrtRoute(startId, endId);
							route.setRouteDescription(600 + " " + moveTime);
							route.setTravelTime(1.5 * moveTime + 600);
						}
						route.setDistance(distance);
						leg.setRoute(route);
						leg.setTravelTime(moveTime);
						time += moveTime;
						legs.add(leg);
						coord = l.fromNode.getCoord();
					}
					stop = l.fromNode.stop;
					mode = l.mode;
				}
				if (coord == null) {
					coord = l.fromNode.getCoord();
					mode = l.mode;
				}
			}
		}
		if(!mode.equals(TransportMode.pt) && !mode.equals(TransportMode.transit_walk)) {
			TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkNode n = ((TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkNode)p.nodes.get(p.nodes.size()-1));
			distance = CoordUtils.calcEuclideanDistance(coord, n.getCoord());
			if(distance>0 || !startLinkId.equals(n.stop.getLinkId())) {
				distance = CoordUtils.calcEuclideanDistance(coord, n.getCoord());
				leg = PopulationUtils.createLeg(mode);
				moveTime = distance / TransitRouterParams.avSpeed;
				route = new DrtRoute(stop.getLinkId(), n.stop.getLinkId());
				route.setRouteDescription(600+" "+moveTime);
				route.setTravelTime(1.5*moveTime + 600);
				route.setDistance(distance);
				leg.setRoute(route);
				leg.setTravelTime(moveTime);
				legs.add(leg);
			}
			coord = n.getCoord();
			stop = n.stop;
		}
		if(!stop.getLinkId().equals(endLinkId)) {
			if(coord==null) {
				TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkLink l = ((TransitRouterNetworkFirstLastAVPT.TransitRouterNetworkLink)p.links.get(p.links.size()-1));
				coord = l.toNode.getCoord();
			}
			distance = CoordUtils.calcEuclideanDistance(coord, toCoord);
			leg = PopulationUtils.createLeg(TransportMode.transit_walk);
			moveTime = distance / this.config.beelineWalkSpeed;
			route = RouteUtils.createGenericRouteImpl(stop.getLinkId(), endLinkId);
			route.setDistance(distance);
			leg.setRoute(route);
			leg.setTravelTime(moveTime);
			legs.add(leg);
		}
		return legs;
	}

	private List<PlanElement> fillWithActivities(List<PlanElement> baseTrip) {
		List<PlanElement> trip = new ArrayList<>();
		for (PlanElement planElement : baseTrip) {
			Leg leg = (Leg) planElement;
			trip.add(leg);
			Activity act = PopulationUtils.createActivityFromLinkId("pt interaction", leg.getRoute().getEndLinkId());
			act.setMaximumDuration(0.0D);
			trip.add(act);
		}
		trip.remove(trip.size() - 1);
		return trip;
	}

	public TransitRouterNetworkFirstLastAVPT getTransitRouterNetwork() {
		return this.transitNetwork;
	}

	protected TransitRouterNetworkFirstLastAVPT getTransitNetwork() {
		return transitNetwork;
	}

	protected MultiNodeDijkstra getDijkstra() {
		return dijkstra;
	}

	@Override
	public List<? extends PlanElement> calcRoute(RoutingRequest request) {
		return null;
	}
}
