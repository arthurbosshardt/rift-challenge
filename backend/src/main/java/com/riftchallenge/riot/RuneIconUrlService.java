package com.riftchallenge.riot;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RuneIconUrlService {

    private static final String BASE_URL = "https://ddragon.leagueoflegends.com/cdn/img/";

    private static final Map<Integer, String> TREE_ICON_PATHS = Map.of(
            8000, "perk-images/Styles/7201_Precision.png",
            8100, "perk-images/Styles/7200_Domination.png",
            8200, "perk-images/Styles/7202_Sorcery.png",
            8300, "perk-images/Styles/7203_Whimsy.png",
            8400, "perk-images/Styles/7204_Resolve.png"
    );

    private static final Map<Integer, String> KEYSTONE_ICON_PATHS = Map.ofEntries(
            // Precision
            Map.entry(8005, "perk-images/Styles/Precision/PressTheAttack/PressTheAttack.png"),
            Map.entry(8008, "perk-images/Styles/Precision/LethalTempo/LethalTempoTemp.png"),
            Map.entry(8021, "perk-images/Styles/Precision/FleetFootwork/FleetFootwork.png"),
            Map.entry(8010, "perk-images/Styles/Precision/Conqueror/Conqueror.png"),
            // Domination
            Map.entry(8112, "perk-images/Styles/Domination/Electrocute/Electrocute.png"),
            Map.entry(8124, "perk-images/Styles/Domination/Predator/Predator.png"),
            Map.entry(8128, "perk-images/Styles/Domination/DarkHarvest/DarkHarvest.png"),
            Map.entry(9923, "perk-images/Styles/Domination/HailOfBlades/HailOfBlades.png"),
            // Sorcery
            Map.entry(8214, "perk-images/Styles/Sorcery/SummonAery/SummonAery.png"),
            Map.entry(8229, "perk-images/Styles/Sorcery/ArcaneComet/ArcaneComet.png"),
            Map.entry(8230, "perk-images/Styles/Sorcery/PhaseRush/PhaseRush.png"),
            // Resolve
            Map.entry(8437, "perk-images/Styles/Resolve/GraspOfTheUndying/GraspOfTheUndying.png"),
            Map.entry(8439, "perk-images/Styles/Resolve/VeteranAftershock/VeteranAftershock.png"),
            Map.entry(8465, "perk-images/Styles/Resolve/Guardian/Guardian.png"),
            // Inspiration
            Map.entry(8351, "perk-images/Styles/Inspiration/GlacialAugment/GlacialAugment.png"),
            Map.entry(8360, "perk-images/Styles/Inspiration/UnsealedSpellbook/UnsealedSpellbook.png"),
            Map.entry(8369, "perk-images/Styles/Inspiration/FirstStrike/FirstStrike.png")
    );

    public String primaryRuneIconUrl(int perkId) {
        String path = KEYSTONE_ICON_PATHS.get(perkId);
        return path == null ? null : BASE_URL + path;
    }

    public String treeIconUrl(int styleId) {
        String path = TREE_ICON_PATHS.get(styleId);
        return path == null ? null : BASE_URL + path;
    }
}
