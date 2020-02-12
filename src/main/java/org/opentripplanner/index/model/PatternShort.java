package org.opentripplanner.index.model;

import com.beust.jcommander.internal.Lists;
import org.opentripplanner.model.TripPattern;

import java.util.Collection;
import java.util.List;

public class PatternShort {

    public String id;
    public String desc;
    
    public PatternShort (TripPattern pattern) {
        // TODO OTP2 - Refactor to use the pattern ID
        id = pattern.getCode();
        desc = pattern.name;
    }
    
    public static List<PatternShort> list (Collection<TripPattern> in) {
        List<PatternShort> out = Lists.newArrayList();
        for (TripPattern pattern : in) out.add(new PatternShort(pattern));
        return out;
    }    
    
}