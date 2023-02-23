package org.matsim.firstLastAVPTRouter;

import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.router.MainModeIdentifier;

import java.util.List;
import java.util.Set;


public final class MainModeIdentifierFirstLastAVPT implements MainModeIdentifier {
    private final Set<String> mainModes;
    public MainModeIdentifierFirstLastAVPT(Set<String> mainModes) {
        this.mainModes = mainModes;
    }

    public String identifyMainMode(List<? extends PlanElement> tripElements) {
        return tripElements.stream().filter(planElement -> planElement instanceof Leg && mainModes.contains(((Leg) planElement).getMode())).findFirst().map(planElement -> ((Leg) planElement).getMode()).orElse("pt");
    }
}
