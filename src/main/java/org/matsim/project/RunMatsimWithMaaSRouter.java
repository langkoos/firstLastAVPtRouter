/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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
package org.matsim.project;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.eventsBasedPTRouter.stopStopTimes.StopStopTimeCalculatorImpl;
import org.matsim.contrib.eventsBasedPTRouter.waitTimes.WaitTimeStuckCalculator;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.firstLastAVPTRouter.TransitRouterFirstLastAVPTFactory;
import org.matsim.firstLastAVPTRouter.TransitRouterNetworkFirstLastAVPT;
import org.matsim.firstLastAVPTRouter.stopStopTimes.StopStopTimeCalculatorAV;
import org.matsim.firstLastAVPTRouter.waitTimes.WaitTimeCalculatorAV;
import org.matsim.pt.config.TransitConfigGroup;
import org.matsim.pt.config.TransitRouterConfigGroup;
import org.matsim.pt.router.TransitRouterNetwork;
import org.matsim.vis.otfvis.OTFVisConfigGroup;

import static org.matsim.contrib.drt.run.DrtControlerCreator.createControler;

/**
 * @author nagel
 *
 */
public class RunMatsimWithMaaSRouter {

	public static void main(String[] args) {
		Config config;
		if ( args==null || args.length==0 || args[0]==null ){
			config = ConfigUtils.loadConfig( "scenarios/TP_Experiment_start/config.xml", new MultiModeDrtConfigGroup(), new OTFVisConfigGroup());
		} else {
			config = ConfigUtils.loadConfig( args, new MultiModeDrtConfigGroup(), new OTFVisConfigGroup());
		}

		config.controler().setOverwriteFileSetting( OverwriteFileSetting.deleteDirectoryIfExists );
		Controler controler = createControler(config,false);
		// possibly modify config here

		// ---
		
		Scenario scenario = controler.getScenario();
//		scenario.
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
		
		controler.run();
	}
	
}
