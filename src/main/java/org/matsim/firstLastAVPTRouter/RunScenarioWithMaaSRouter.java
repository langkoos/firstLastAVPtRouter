package org.matsim.firstLastAVPTRouter;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.eventsBasedPTRouter.stopStopTimes.StopStopTimeCalculatorImpl;
import org.matsim.contrib.eventsBasedPTRouter.waitTimes.WaitTimeStuckCalculator;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.firstLastAVPTRouter.stopStopTimes.StopStopTimeCalculatorAV;
import org.matsim.firstLastAVPTRouter.waitTimes.WaitTimeCalculatorAV;

import static org.matsim.contrib.drt.run.DrtControlerCreator.createControler;

public class RunScenarioWithMaaSRouter {
    public static void main(String[] args) {
        Controler controler = createControler(ConfigUtils.loadConfig(args[0]),false);
        Scenario scenario = controler.getScenario();
        int totalTime = (int) (scenario.getConfig().qsim().getEndTime().seconds() - scenario.getConfig().qsim().getStartTime().seconds());
        final WaitTimeStuckCalculator waitTimeCalculator = new WaitTimeStuckCalculator(scenario.getPopulation(), scenario.getTransitSchedule(), scenario.getConfig().travelTimeCalculator().getTraveltimeBinSize(), totalTime);
        controler.getEvents().addHandler(waitTimeCalculator);
        final WaitTimeCalculatorAV waitTimeCalculatorAV = new WaitTimeCalculatorAV(scenario.getPopulation(), scenario.getTransitSchedule(), scenario.getConfig().travelTimeCalculator().getTraveltimeBinSize(), totalTime);
        controler.getEvents().addHandler(waitTimeCalculatorAV);
        final StopStopTimeCalculatorImpl stopStopTimeCalculator = new StopStopTimeCalculatorImpl(scenario.getTransitSchedule(), scenario.getConfig().travelTimeCalculator().getTraveltimeBinSize(), totalTime);
        controler.getEvents().addHandler(stopStopTimeCalculator);
        final StopStopTimeCalculatorAV stopStopTimeCalculatorAV = new StopStopTimeCalculatorAV(scenario.getTransitSchedule(), scenario.getConfig().travelTimeCalculator().getTraveltimeBinSize(), totalTime);
        controler.getEvents().addHandler(stopStopTimeCalculatorAV);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addRoutingModuleBinding("pt").toProvider(new TransitRouterFirstLastAVPTFactory(scenario, waitTimeCalculator.get(), waitTimeCalculatorAV.get(), stopStopTimeCalculator.get(), stopStopTimeCalculatorAV.get(), TransitRouterNetworkFirstLastAVPT.NetworkModes.PT_AV));
            }
        });
    }
}
