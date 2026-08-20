package com.riftchallenge.riot;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SummonerSpellIconUrlService {

    private static final Map<Integer, String> SPELL_NAMES = Map.ofEntries(
            Map.entry(1, "SummonerBoost"),
            Map.entry(3, "SummonerExhaust"),
            Map.entry(4, "SummonerFlash"),
            Map.entry(6, "SummonerHaste"),
            Map.entry(7, "SummonerHeal"),
            Map.entry(11, "SummonerSmite"),
            Map.entry(12, "SummonerTeleport"),
            Map.entry(13, "SummonerMana"),
            Map.entry(14, "SummonerDot"),
            Map.entry(21, "SummonerBarrier"),
            Map.entry(32, "SummonerSnowball")
    );

    public String buildIconUrl(int spellId) {
        String name = SPELL_NAMES.get(spellId);
        if (name == null) {
            return null;
        }
        return "https://ddragon.leagueoflegends.com/cdn/%s/img/spell/%s.png".formatted(DDragonVersions.CURRENT, name);
    }
}
