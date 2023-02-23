package org.matsim.firstLastAVPTRouter.waitTimes;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonStuckEventHandler;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.eventsBasedPTRouter.waitTimes.WaitTime;
import org.matsim.contrib.eventsBasedPTRouter.waitTimes.WaitTimeData;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.routes.DefaultTransitPassengerRouteFactory;
import org.matsim.pt.transitSchedule.api.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Save waiting times of agents while mobsim is running
 *
 * @author sergioo
 */
@Singleton
public class WorstWaitTimeStuckCalculator implements PersonDepartureEventHandler, PersonEntersVehicleEventHandler, PersonStuckEventHandler, Provider<WaitTime> {

    //Attributes
    private final double timeSlot;
    private final Map<Tuple<Id<TransitLine>, Id<TransitRoute>>, Map<Id<TransitStopFacility>, WaitTimeData>> waitTimes = new HashMap<>(1000);
    private final Map<Tuple<Id<TransitLine>, Id<TransitRoute>>, Map<Id<TransitStopFacility>, double[]>> scheduledWaitTimes = new HashMap<>(1000);
    private final Map<Id<Person>, Double> agentsWaitingData = new HashMap<>();
    private final Map<Id<Person>, Integer> agentsCurrentLeg = new HashMap<>();
    private final Population population;

    //Constructors
    @Inject
    public WorstWaitTimeStuckCalculator(final Population population, final TransitSchedule transitSchedule, final Config config, final EventsManager eventsManager) {
        this(population, transitSchedule, config.travelTimeCalculator().getTraveltimeBinSize(), (int) (config.qsim().getEndTime().seconds()-config.qsim().getStartTime().seconds()));
        eventsManager.addHandler(this);
    }
    public WorstWaitTimeStuckCalculator(final Population population, final TransitSchedule transitSchedule, final int timeSlot, final int totalTime) {
        this.population = population;
        this.timeSlot = timeSlot;
        for(TransitLine line:transitSchedule.getTransitLines().values())
            for(TransitRoute route:line.getRoutes().values()) {
                double[] sortedDepartures = new double[route.getDepartures().size()];
                int d=0;
                for(Departure departure:route.getDepartures().values())
                    sortedDepartures[d++] = departure.getDepartureTime();
                Arrays.sort(sortedDepartures);
                Map<Id<TransitStopFacility>, WaitTimeData> stopsMap = new HashMap<>(100);
                Map<Id<TransitStopFacility>, double[]> stopsScheduledMap = new HashMap<>(100);
                List<TransitRouteStop> stops = route.getStops();
                for (TransitRouteStop stop : stops) {
                    stopsMap.put(stop.getStopFacility().getId(), new WorstWaitTimeDataArray(totalTime / timeSlot + 1));
                    double[] cacheWaitTimes = new double[totalTime / timeSlot + 1];
                    for (int i = 0; i < cacheWaitTimes.length; i++) {
                        double endTime = timeSlot * (i + 1);
                        if (endTime > 24 * 3600)
                            endTime -= 24 * 3600;
                        cacheWaitTimes[i] = Double.POSITIVE_INFINITY;
                        for (double departure : sortedDepartures) {
                            double arrivalTime = departure + (stop.getArrivalOffset().seconds() != Double.POSITIVE_INFINITY ? stop.getArrivalOffset().seconds() : stop.getDepartureOffset().seconds());
                            if (arrivalTime >= endTime) {
                                cacheWaitTimes[i] = arrivalTime - endTime;
                                break;
                            }
                        }
                        if (cacheWaitTimes[i] == Double.POSITIVE_INFINITY)
                            cacheWaitTimes[i] = sortedDepartures[0] + 24 * 3600 + (stop.getArrivalOffset().seconds() != Double.POSITIVE_INFINITY ? stop.getArrivalOffset().seconds() : stop.getDepartureOffset().seconds()) - endTime;
                    }
                    stopsScheduledMap.put(stop.getStopFacility().getId(), cacheWaitTimes);
                }
                Tuple<Id<TransitLine>, Id<TransitRoute>> key = new Tuple<>(line.getId(), route.getId());
                waitTimes.put(key, stopsMap);
                scheduledWaitTimes.put(key, stopsScheduledMap);
            }
    }

    //Methods
    private double getRouteStopWaitTime(Id<TransitLine> lineId, Id<TransitRoute> routeId, Id<TransitStopFacility> stopId, double time) {
        Tuple<Id<TransitLine>, Id<TransitRoute>> key = new Tuple<>(lineId, routeId);
        WaitTimeData waitTimeData = waitTimes.get(key).get(stopId);
        switch (waitTimeData.getNumData((int) (time / timeSlot))) {
            case 0:
                double[] waitTimes = scheduledWaitTimes.get(key).get(stopId);
                return waitTimes[(int) (time / timeSlot) < waitTimes.length ? (int) (time / timeSlot) : (waitTimes.length - 1)];
            default:
                return waitTimeData.getWaitTime((int) (time / timeSlot));
        }
    }
    @Override
    public void reset(int iteration) {
        waitTimes.values().stream().flatMap(routeData -> routeData.values().stream()).forEach(WaitTimeData::resetWaitTimes);
        agentsWaitingData.clear();
        agentsCurrentLeg.clear();
    }
    @Override
    public void handleEvent(PersonDepartureEvent event) {
        Integer currentLeg = agentsCurrentLeg.get(event.getPersonId());
        if(currentLeg == null)
            currentLeg = 0;
        else
            currentLeg++;
        agentsCurrentLeg.put(event.getPersonId(), currentLeg);
        if(event.getLegMode().equals("pt") && agentsWaitingData.get(event.getPersonId())==null)
            agentsWaitingData.put(event.getPersonId(), event.getTime());
        else if(agentsWaitingData.get(event.getPersonId())!=null)
            throw new RuntimeException("Departing with old data");
    }


    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {
        Double startWaitingTime = agentsWaitingData.get(event.getPersonId());
        if(startWaitingTime!=null) {
            int legs = 0, currentLeg = agentsCurrentLeg.get(event.getPersonId());
            List<PlanElement> planElements = population.getPersons().get(event.getPersonId()).getSelectedPlan().getPlanElements();
            PLAN_ELEMENTS:
            for (PlanElement planElement : planElements) {
                if (planElement instanceof Leg) {
                    if (currentLeg == legs) {
                        WaitTimeData data = getWaitTimeData((Leg) planElement, this.waitTimes);
                        data.addWaitTime((int) (startWaitingTime / timeSlot), event.getTime() - startWaitingTime);
                        agentsWaitingData.remove(event.getPersonId());
                        break;
                    } else
                        legs++;
                }
            }
        }
    }

    private WaitTimeData getWaitTimeData(Leg planElement, Map<Tuple<Id<TransitLine>, Id<TransitRoute>>, Map<Id<TransitStopFacility>, WaitTimeData>> waitTimes) {
        Route route = planElement.getRoute();
        TransitPassengerRoute eRoute = (TransitPassengerRoute) new DefaultTransitPassengerRouteFactory().createRoute(route.getStartLinkId(), route.getEndLinkId());
        eRoute.setStartLinkId(route.getStartLinkId());
        eRoute.setEndLinkId(route.getEndLinkId());
        eRoute.setRouteDescription(route.getRouteDescription());
        return waitTimes.get(new Tuple<>(eRoute.getLineId(), eRoute.getRouteId())).get(eRoute.getAccessStopId());
    }

    @Override
    public void handleEvent(PersonStuckEvent event) {
        Double startWaitingTime = agentsWaitingData.get(event.getPersonId());
        if(startWaitingTime!=null) {
            int legs = 0, currentLeg = agentsCurrentLeg.get(event.getPersonId());
            List<PlanElement> planElements = population.getPersons().get(event.getPersonId()).getSelectedPlan().getPlanElements();
            PLAN_ELEMENTS:
            for (PlanElement planElement : planElements) {
                if (planElement instanceof Leg) {
                    if (currentLeg == legs) {
                        WaitTimeData data = getWaitTimeData((Leg) planElement, this.waitTimes);
                        if (data != null)
                            data.addWaitTime((int) (startWaitingTime / timeSlot), event.getTime() - startWaitingTime);
                        agentsWaitingData.remove(event.getPersonId());
                        break;
                    } else
                        legs++;
                }
            }
        }
    }

    @Override
    public WaitTime get() {
        return new WaitTime() {

            private static final long serialVersionUID = 1L;

            @Override
            public double getRouteStopWaitTime(Id<TransitLine> lineId, Id<TransitRoute> routeId, Id<TransitStopFacility> stopId, double time) {
                return WorstWaitTimeStuckCalculator.this.getRouteStopWaitTime(lineId, routeId, stopId, time);
            }

        };
    }
}


