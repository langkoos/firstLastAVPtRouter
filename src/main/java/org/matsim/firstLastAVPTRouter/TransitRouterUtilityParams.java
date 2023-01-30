package org.matsim.firstLastAVPTRouter;

import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;

public class TransitRouterUtilityParams {
    final double marginalUtilityOfTravelTimePt_s;
    final double marginalUtilityOfTravelDistancePt_m;
    final double marginalUtilityWait_s;
    final double marginalUtilityAV_s;
    final double marginalUtilityWalk_s;
    final double marginalUtilityAV_m;
    final double marginalUtilityWalk_m;
    final double utilityLineSwitch;
    final double initialCostAV;
    final double initialCostWalk;
    final double initialCostPT;

    public TransitRouterUtilityParams(PlanCalcScoreConfigGroup pcsConfig){
        this.marginalUtilityOfTravelTimePt_s = pcsConfig.getModes().get("pt").getMarginalUtilityOfTraveling()/3600;
        this.marginalUtilityOfTravelDistancePt_m = pcsConfig.getModes().get("pt").getMarginalUtilityOfDistance()+pcsConfig.getMarginalUtilityOfMoney()*pcsConfig.getModes().get("pt").getMonetaryDistanceRate();
        this.marginalUtilityWait_s = pcsConfig.getMarginalUtlOfWaitingPt_utils_hr()/3600;
        this.marginalUtilityAV_s = pcsConfig.getModes().get("drt").getMarginalUtilityOfTraveling()/3600;
        this.marginalUtilityWalk_s = pcsConfig.getModes().get("walk").getMarginalUtilityOfTraveling()/3600;
        this.marginalUtilityAV_m = pcsConfig.getModes().get("drt").getMarginalUtilityOfDistance() + pcsConfig.getMarginalUtilityOfMoney() * pcsConfig.getModes().get("drt").getMonetaryDistanceRate();
        this.marginalUtilityWalk_m = pcsConfig.getModes().get("walk").getMarginalUtilityOfDistance() + pcsConfig.getMarginalUtilityOfMoney() * pcsConfig.getModes().get("walk").getMonetaryDistanceRate();
        this.utilityLineSwitch = pcsConfig.getUtilityOfLineSwitch();
        this.initialCostAV = -pcsConfig.getModes().get("drt").getConstant();
        this.initialCostPT = -pcsConfig.getModes().get("pt").getConstant();
        this.initialCostWalk = -pcsConfig.getModes().get("walk").getConstant();
    }
}

